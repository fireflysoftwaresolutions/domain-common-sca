import importlib.util
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

MODULE = Path(__file__).resolve().parents[1] / 'prepare_recovery_sdk_fixture.py'
spec = importlib.util.spec_from_file_location('fixture', MODULE)
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)

class CandidateIsolationTest(unittest.TestCase):
    def test_candidate_changes_only_explicit_sdk_version_and_preserves_parent(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'pom.xml'
            path.write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><parent><artifactId>core-common-sca-mgmt</artifactId><version>26.10.6</version></parent><artifactId>core-common-sca-mgmt-sdk</artifactId></project>')
            fixture.candidate_sdk(path, '26.10.6-recovery-source-test-SNAPSHOT')
            root = ET.parse(path).getroot()
            self.assertEqual(root.find('m:parent/m:version', fixture.NS).text, '26.10.6')
            self.assertEqual(root.find('m:version', fixture.NS).text, '26.10.6-recovery-source-test-SNAPSHOT')
            with self.assertRaises(ValueError): fixture.candidate_sdk(path, 'another')

    def test_unexpected_parent_shape_fails_before_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); path = root / 'pom.xml'; original = '<project><relativePath/><relativePath/></project>'
            path.write_text(original)
            with self.assertRaises(ValueError): fixture.source_parent(root, root)
            self.assertEqual(path.read_text(), original)
