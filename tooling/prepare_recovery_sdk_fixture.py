"""Hosted source qualification only: original producer, generated candidate SDK, actual Domain reactor."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
ET.register_namespace('', NS['m'])
PARENT_PIN = '6442bd51733ade4d5f4583e1afe812dd2c520b40'
OPERATIONS = {
    'createRecoveryOperation': ('', '201'),
    'issueRecoveryChallenge': ('/{operationId}/challenges', '201'),
    'verifyRecoveryChallenge': ('/{operationId}/verifications', '202'),
    'reserveRecoveryVerification': ('/{operationId}/verifications/reservations', '201'),
    'completeRecoveryVerification': ('/{operationId}/verifications/complete', '202'),
    'consumeRecoveryAuthorization': ('/{operationId}/authorizations', '200'),
    'verifyRecoveryAuthorization': ('/{operationId}/authorizations/verify', '200'),
    'recordRecoveryExecution': ('/{operationId}/executions', '204'),
}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def head(path):
    return subprocess.check_output(['git', '-C', str(path), 'rev-parse', 'HEAD'], text=True).strip()


def source_parent(project, parent):
    path = project / 'pom.xml'
    original = path.read_text()
    if original.count('<relativePath/>') != 1:
        raise ValueError('Ambiguous parent reference')
    relative = os.path.relpath(parent / 'pom.xml', project)
    path.write_text(original.replace('<relativePath/>', f'<relativePath>{relative}</relativePath>', 1))


def candidate_sdk(pom, version):
    tree = ET.parse(pom)
    model = tree.getroot()
    if model.find('m:artifactId', NS).text != 'core-common-sca-mgmt-sdk' or model.find('m:version', NS) is not None:
        raise ValueError('Unexpected SDK version model')
    element = ET.Element(f"{{{NS['m']}}}version")
    element.text = version
    model.insert(list(model).index(model.find('m:artifactId', NS)) + 1, element)
    # SDK project.version would also remap provided transitive siblings through inherited dependency management.
    management = ET.SubElement(model, f"{{{NS['m']}}}dependencyManagement")
    managed = ET.SubElement(management, f"{{{NS['m']}}}dependencies")
    for module in ('interfaces', 'models', 'core', 'web'):
        dependency = ET.SubElement(managed, f"{{{NS['m']}}}dependency")
        for name, value in [('groupId', 'io.fireflysoftware'), ('artifactId', 'core-common-sca-mgmt-' + module), ('version', '26.10.6')]:
            ET.SubElement(dependency, f"{{{NS['m']}}}{name}").text = value
    # Retain the direct provided web sibling too.

    for dependency in model.findall('m:dependencies/m:dependency', NS):
        if dependency.find('m:artifactId', NS).text == 'core-common-sca-mgmt-web':
            ET.SubElement(dependency, f"{{{NS['m']}}}version").text = '26.10.6'
    tree.write(pom, encoding='unicode', xml_declaration=True)


def select_candidate(domain_pom, version):
    tree = ET.parse(domain_pom)
    dependencies = tree.getroot().find('m:dependencyManagement/m:dependencies', NS)
    if dependencies is None or any(d.find('m:artifactId', NS).text == 'core-common-sca-mgmt-sdk' for d in dependencies):
        raise ValueError('Unexpected Domain dependency-management model')
    dependency = ET.SubElement(dependencies, f"{{{NS['m']}}}dependency")
    for name, value in [('groupId', 'io.fireflysoftware'), ('artifactId', 'core-common-sca-mgmt-sdk'), ('version', version)]:
        ET.SubElement(dependency, f"{{{NS['m']}}}{name}").text = value
    tree.write(domain_pom, encoding='unicode', xml_declaration=True)


def run(command, label):
    log = ROOT / '.qualification' / (label + '.log')
    with log.open('w') as stream:
        result = subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT)
    if result.returncode:
        # These are source-only synthetic tests. Print bounded build diagnostics, never provider/browser logs.
        for report in ROOT.glob('domain-common-sca-*/target/surefire-reports/TEST-*.xml'):
            suite = ET.parse(report).getroot()
            for case in suite.findall('testcase'):
                for error in case.findall('error') + case.findall('failure'):
                    causes = [line.strip() for line in (error.text or '').splitlines() if 'Caused by:' in line]
                    if causes:
                        print('TEST_CAUSES ' + case.attrib.get('classname', '') + '.' + case.attrib.get('name', '') + ': ' + ' | '.join(causes[:5])[:1800])
        lines = log.read_text(errors='replace').splitlines()
        print('\n'.join(lines[-90:]))
        raise RuntimeError(f'{label} failed with status {result.returncode}')


def main():
    if os.environ.get('GITHUB_ACTIONS') != 'true':
        raise SystemExit('This fixture runs only on the owned hosted runner; local JVM execution is forbidden')
    os.umask(0o077)
    qualification = ROOT / '.qualification'
    core = qualification / 'core'
    parent = qualification / 'firefly-parent'
    pin = (ROOT / 'tooling/recovery-core-source.txt').read_text().strip()
    if not re.fullmatch(r'[0-9a-f]{40}', pin) or head(core) != pin or head(parent) != PARENT_PIN:
        raise SystemExit('Immutable source pins do not match')
    model = ET.parse(parent / 'pom.xml').getroot()
    if model.find('m:parent/m:version', NS).text != '26.10.23' or model.find('m:properties/m:fireflyframework.bom.version', NS).text != '26.10.23':
        raise SystemExit('One exact Jafly 26.10.23 parent/BOM train required')
    sdk = core / 'core-common-sca-mgmt-sdk'
    summary = {'scope': 'Original-producer/generated-SDK/Domain source fixture only', 'core_source': pin,
               'domain_source': head(ROOT), 'source_parent': PARENT_PIN, 'framework_train': '26.10.23',
               'published_artifact_adoption': False, 'native_unlock_qualified': False,
               'original_core_pom_sha256': sha(core / 'pom.xml'), 'original_sdk_pom_sha256': sha(sdk / 'pom.xml'),
               'original_domain_pom_sha256': sha(ROOT / 'pom.xml')}
    version = f'26.10.6-recovery-source-{pin[:12]}-SNAPSHOT'
    source_parent(core, parent)
    source_parent(ROOT, parent)
    candidate_sdk(sdk / 'pom.xml', version)
    select_candidate(ROOT / 'pom.xml', version)
    m2 = Path(os.environ['RUNNER_TEMP']) / 'domain-recovery-sdk-m2'
    common = ['mvn', '-B', '-ntp', f'-Dmaven.repo.local={m2}']
    run(common + ['-f', str(core / 'pom.xml'), '-pl', ':core-common-sca-mgmt-sdk', '-am', 'verify',
                  'org.codehaus.mojo:flatten-maven-plugin:1.8.0:flatten', '-Dflatten.mode=clean', '-DupdatePomFile=false'], 'core-producer')
    # Parse genuine producer YAML with PyYAML supplied by the hosted fixture, never rewrite it.
    import yaml
    spec_path = sdk / 'src/main/resources/openapi.yml'
    spec = yaml.safe_load(spec_path.read_text())
    for operation_id, (suffix, status) in OPERATIONS.items():
        operation = spec['paths']['/api/v1/sca/recovery-operations' + suffix]['post']
        if operation['operationId'] != operation_id or status not in operation['responses']:
            raise RuntimeError('Producer operation/status contract mismatch')
        if {'bearerAuth': []} not in operation.get('security', []) or any(p['name'] == 'X-Idempotency-Key' for p in operation.get('parameters', [])):
            raise RuntimeError('Producer bearer/cache contract mismatch')
    flat = sdk / '.flattened-pom.xml'
    flat_model = ET.parse(flat).getroot()
    if flat_model.find('m:parent', NS) is not None or flat_model.find('m:version', NS).text != version:
        raise RuntimeError('Candidate descriptor must remove released parent inheritance')
    jar = sdk / 'target' / f'core-common-sca-mgmt-sdk-{version}.jar'
    run(common + ['-N', '-f', str(flat), 'org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file',
                  f'-Dfile={jar}', f'-DpomFile={flat}'], 'candidate-install')
    summary.update(candidate_sdk_version=version, candidate_sdk_sha256=sha(jar), candidate_descriptor_sha256=sha(flat),
                   producer_spec_sha256=sha(spec_path), status='GENERATED_SDK_INSTALLED_DOMAIN_PENDING')
    (qualification / 'sdk-domain-summary.json').write_text(json.dumps(summary, indent=2) + '\n')
    if '--candidate-only' in sys.argv:
        print('Generated candidate SDK installed; Domain verification explicitly not executed')
        return
    run(common + ['-f', str(ROOT / 'pom.xml'), 'verify'], 'domain-reactor')
    reports = []
    for report in ROOT.glob('domain-common-sca-*/target/surefire-reports/TEST-*.xml'):
        suite = ET.parse(report).getroot()
        if any(word in suite.attrib.get('name', '') for word in ('Recovery', 'GeneratedRecoverySdk', 'ScopedRecoveryBearer')):
            reports.append({key: suite.attrib.get(key, '0') for key in ('name', 'tests', 'failures', 'errors', 'skipped')})
    required = {'RecoveryAdmissionTest', 'NativeSelfRecoveryPolicyTest', 'NativeRecoveryHttpTest', 'RecoveryOrchestratorTest', 'GeneratedRecoverySdkTest', 'ScopedRecoveryBearerTest', 'RecoveryBoundaryTest', 'RecoveryConfigurationTest', 'RecoveryOpenApiContractTest'}
    if not all(any(r['name'].endswith(name) for r in reports) for name in required) or any(int(r[k]) for r in reports for k in ('failures', 'errors', 'skipped')):
        raise RuntimeError('Required actual Domain/SDK reports missing, failed or skipped')
    summary.update(candidate_sdk_version=version, candidate_sdk_sha256=sha(jar), candidate_descriptor_sha256=sha(flat),
                   producer_spec_sha256=sha(spec_path), reports=reports, status='SOURCE_FIXTURE_PASSED')
    (qualification / 'sdk-domain-summary.json').write_text(json.dumps(summary, indent=2) + '\n')
    print('Original producer, distinct generated SDK and Domain source tests passed; native execution remains separate.')


if __name__ == '__main__':
    main()
