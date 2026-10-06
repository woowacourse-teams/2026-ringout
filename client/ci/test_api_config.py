import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import api_config


class ApiConfigTest(unittest.TestCase):
    def test_운영_빌드는_환경변수_누락과_개발주소를_거부한다(self):
        for env in ({}, {'RINGOUT_API_BASE_URL': api_config.DEVELOPMENT_URL}):
            with patch.dict(os.environ, env, clear=True):
                with self.assertRaises(ValueError):
                    api_config.require_production_url()

    def test_생성된_주소와_환경변수가_다르면_패키징을_거부한다(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)/api_config.GENERATED_PATH
            path.parent.mkdir(parents=True)
            with patch.dict(os.environ, {'RINGOUT_API_BASE_URL': api_config.PRODUCTION_URL}):
                path.write_text(f'const val BASE_URL = "{api_config.DEVELOPMENT_URL}"\n')
                with self.assertRaises(ValueError):
                    api_config.verify_generated(directory, production=True)
                path.write_text(f'const val BASE_URL = "{api_config.PRODUCTION_URL}"\n')
                self.assertEqual(api_config.verify_generated(directory, production=True), api_config.PRODUCTION_URL)

    def test_환경변수가_없으면_로컬은_개발주소를_사용한다(self):
        with patch.dict(os.environ, {}, clear=True):
            self.assertEqual(api_config.configured_url(), api_config.DEVELOPMENT_URL)
