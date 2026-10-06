"""Shared internal/production CD HTTP error diagnostics."""

import io
import json
import unittest
from unittest.mock import patch
from urllib.error import HTTPError

import android_internal_cd as cd


class CDAPIErrorTest(unittest.TestCase):
    def request_error(self, body):
        url = 'https://androidpublisher.googleapis.com/test:commit?private=value'
        error = HTTPError(url, 400, 'Bad Request', {}, io.BytesIO(body))
        with patch.object(cd, 'urlopen', side_effect=error):
            with self.assertRaises(cd.CDError) as caught:
                cd.request_json(url, 'secret-token', 'POST', {})
        return str(caught.exception)

    def test_구글_오류_메시지는_남기고_토큰과_이메일과_기타_필드는_숨긴다(self):
        body = json.dumps({'error': {
            'message': 'Declaration required\nsecret-token user@example.com',
            'details': 'PRIVATE_DETAILS',
        }}).encode()
        result = self.request_error(body)
        self.assertIn('HTTP 400 (POST https://androidpublisher.googleapis.com/test:commit)', result)
        self.assertIn('Declaration required [REDACTED] [REDACTED_EMAIL]', result)
        for value in ('secret-token', 'user@example.com', 'PRIVATE_DETAILS', '?private', '\n'):
            self.assertNotIn(value, result)

    def test_깃허브_최상위_오류_메시지도_출력한다(self):
        self.assertIn('Resource not accessible', self.request_error(b'{"message":"Resource not accessible"}'))

    def test_잘못된_응답에서도_HTTP_오류를_보존한다(self):
        for body in (b'<html>error</html>', b'', b'[]', b'null', b'{}', b'{"error":{"message":42}}'):
            with self.subTest(body=body):
                result = self.request_error(body)
                self.assertIn('HTTP 400', result)
                self.assertNotIn(' — ', result)

    def test_긴_메시지는_이천자로_제한한다(self):
        result = self.request_error(json.dumps({'error': {'message': 'x' * 3000}}).encode())
        self.assertEqual(result.split(' — ')[1], 'x' * 2000)

    def test_읽기_제한을_넘는_응답은_HTTP_오류만_출력한다(self):
        result = self.request_error(json.dumps({'error': {'message': 'x' * 70000}}).encode())
        self.assertIn('HTTP 400', result)
        self.assertNotIn(' — ', result)


if __name__ == '__main__':
    unittest.main()
