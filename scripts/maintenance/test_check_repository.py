"""Regression checks for precise translation-resource exceptions."""
import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('repository_checker', Path(__file__).with_name('check_repository.py'))
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)

class TranslationPolicyTest(unittest.TestCase):
    def test_chinese_is_allowed_only_in_zh_translation_values(self):
        good = '{"Hello":{"zh":"\\u4f60\\u597d","fr":"Bonjour"}}'
        self.assertEqual([], checker.language_errors(checker.FRONTEND_MESSAGES, good))
        self.assertTrue(checker.language_errors(checker.FRONTEND_MESSAGES, '{"Hello":{"zh":"Hi"}}'))
        self.assertTrue(checker.language_errors(checker.FRONTEND_MESSAGES, '{"Hello":{"zh":"Hi","fr":"\\u4f60"}}'))
        self.assertTrue(checker.language_errors(checker.FRONTEND_MESSAGES, '{"\\u4f60":{"zh":"Hi","fr":"Bonjour"}}'))

    def test_backend_exception_is_restricted_to_value_text(self):
        self.assertEqual([], checker.language_errors(checker.CHINESE_API_MESSAGES, 'api.key=\u4f60\u597d'))
        self.assertTrue(checker.language_errors(checker.CHINESE_API_MESSAGES, '\u4f60=Hello'))
        self.assertTrue(checker.language_errors(checker.CHINESE_API_MESSAGES, '# \u4f60'))
        self.assertTrue(checker.language_errors('backend/java/example.properties', 'api.key=\u4f60'))

    def test_source_and_unknown_catalogue_paths_remain_checked(self):
        for path in ['frontend/src/example.js', 'frontend/src/i18n/other.json', 'docs/example.md']:
            self.assertTrue(checker.language_errors(path, '\u4f60\u597d'))
        self.assertEqual([], checker.language_errors('docs/development/page-api-integration.md', '\u4f60\u597d'))

if __name__ == '__main__':
    unittest.main()
