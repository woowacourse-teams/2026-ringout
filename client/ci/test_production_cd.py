"""Production routing, credentials, release state and retry contracts."""

import base64
import json
import zipfile
import hashlib
import os
from pathlib import Path
import plistlib
import tempfile
import unittest
from unittest.mock import patch

import android_internal_cd as internal
import android_production_cd as production
import ios_cd_gate
import ios_internal
import production_config as config
import verify_ios_firebase


class ProductionCDTest(unittest.TestCase):
    def test_운영_빌드_번호는_내부_빌드와_다르며_범위를_넘지_않는다(self):
        self.assertEqual(config.production_number('261010020'), '261010021')
        with self.assertRaises(ValueError):
            config.production_number('2100000000')

    def test_main의_동일_커밋_성공만_운영_배포를_허용한다(self):
        run = dict(id=1, head_sha='a'*40, head_branch='main', event='push',
                   head_repository={'full_name': 'team/repo'}, status='completed', conclusion='success')
        self.assertEqual(internal.choose_run([run], 'a'*40, 'team/repo', 'main')[0], 'deploy')
        self.assertEqual(ios_cd_gate.decision([run], 'a'*40, 'team/repo', 'main')[0], 'deploy')
        for change in ({'head_branch': 'develop'}, {'head_sha': 'b'*40}, {'event': 'pull_request'},
                       {'head_repository': {'full_name': 'other/repo'}}):
            with self.subTest(change=change):
                wrong = dict(run, **change)
                self.assertEqual(internal.choose_run([wrong], 'a'*40, 'team/repo', 'main')[0], 'wait')
                self.assertEqual(ios_cd_gate.decision([wrong], 'a'*40, 'team/repo', 'main')[0], 'wait')
        for conclusion in ('failure', 'cancelled', 'timed_out'):
            self.assertEqual(internal.choose_run([dict(run, conclusion=conclusion)], 'a'*40, 'team/repo', 'main')[0], 'fail')
            self.assertEqual(ios_cd_gate.decision([dict(run, conclusion=conclusion)], 'a'*40, 'team/repo', 'main')[0], 'skip')

    def test_운영_AAB는_예약번호와_출처와_운영설정을_모두_검증한다(self):
        with tempfile.TemporaryDirectory() as directory:
            sha = 'a'*40
            folder = Path(directory)/f'ringout-release-aab-21-{sha[:12]}-attempt1'
            folder.mkdir()
            aab = folder/(folder.name + '.aab')
            with zipfile.ZipFile(aab, 'w') as archive:
                archive.writestr('base/manifest/AndroidManifest.xml', b'manifest')
            digest = hashlib.sha256(aab.read_bytes()).hexdigest()
            cert = hashlib.sha256(b'certificate').hexdigest().upper()
            metadata = dict(applicationId=internal.APPLICATION_ID, channel='release', branch='main',
                            commit=sha, runId='123', runAttempt='1', firebaseProjectId='ringout-prod',
                            uploadCertificateSha256=cert, versionCode=21, versionName='1.0.0',
                            sha256=digest, apiBaseUrl=config.PRODUCTION_API)
            path = folder/'build-metadata.json'
            path.write_text(json.dumps(metadata))
            (folder/'sha256.txt').write_text(f'{digest}  {aab.name}')
            from types import SimpleNamespace
            def tool(args, **kwargs):
                value = 'jar verified' if args[0] == 'jarsigner' else b'-----BEGIN CERTIFICATE-----\n' + base64.b64encode(b'certificate') + b'\n-----END CERTIFICATE-----'
                return SimpleNamespace(returncode=0, stdout=value)
            with patch.object(internal, 'declared_android_version', return_value=('20', '1.0.0')), patch.object(internal, 'expected_certificate', return_value=cert), patch.object(internal.subprocess, 'run', side_effect=tool):
                self.assertEqual(internal.verify_artifact(directory, sha, '123', '1', 'main')[0], aab)
                for change in ({'channel': 'internal'}, {'commit': 'b'*40}, {'versionCode': 20},
                               {'firebaseProjectId': 'ringout-8abf2'}, {'apiBaseUrl': 'https://dev-api.ringout.my'},
                               {'sha256': 'bad'}, {'runAttempt': '2'}):
                    with self.subTest(change=change):
                        path.write_text(json.dumps(dict(metadata, **change)))
                        with self.assertRaises(internal.CDError):
                            internal.verify_artifact(directory, sha, '123', '1', 'main')

    def test_운영_준비는_main_push에서만_가능하다(self):
        for ref, event in [('refs/heads/develop', 'push'), ('refs/heads/main', 'pull_request'), ('refs/heads/main', 'workflow_dispatch')]:
            with patch.dict(os.environ, {'GITHUB_REF': ref, 'GITHUB_EVENT_NAME': event}):
                with self.assertRaises(ValueError):
                    config.require_main()

    def test_운영_준비를_반복해도_빌드번호를_두번_올리지_않는다(self):
        committed = {config.ANDROID_PATH: 'versionCode = 20\n', config.IOS_PATH: 'CURRENT_PROJECT_VERSION = 20;\n'}
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for path in committed:
                (root/path).parent.mkdir(parents=True, exist_ok=True)
            with patch.object(config, 'ROOT', root), patch.object(config, 'source', side_effect=committed.__getitem__), patch.object(config, 'require_main'), patch.dict(os.environ, {'RINGOUT_API_BASE_URL': config.PRODUCTION_API}):
                for platform in ('android', 'ios'):
                    config.prepare(platform)
                    config.prepare(platform)
                self.assertIn('versionCode = 21', (root/config.ANDROID_PATH).read_text())
                self.assertIn('CURRENT_PROJECT_VERSION = 21;', (root/config.IOS_PATH).read_text())

    def test_기존_운영_릴리스를_보존하고_중복_배포는_상태를_되돌리지_않는다(self):
        active = {'status': 'completed', 'versionCodes': ['10']}
        self.assertEqual(production.production_releases([active], '11'), [active, {'status': 'draft', 'versionCodes': ['11']}])
        for status in ('draft', 'completed', 'inProgress', 'halted'):
            self.assertIsNone(production.production_releases([{'status': status, 'versionCodes': ['11']}], '11'))
        for status in ('draft', 'inProgress', 'halted'):
            with self.assertRaises(internal.CDError):
                production.production_releases([{'status': status, 'versionCodes': ['12']}], '11')

    def test_운영_iOS는_내부전용이_아니며_자동_빌드번호_변경을_하지_않는다(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)/'export.plist'
            ios_internal.export_options('uuid', path, production=True)
            options = plistlib.loads(path.read_bytes())
            self.assertFalse(options['testFlightInternalTestingOnly'])
            self.assertFalse(options['manageAppVersionAndBuildNumber'])
            self.assertEqual(options['destination'], 'upload')

    def test_운영_iOS는_개발_Firebase와_내장설정_불일치를_거부한다(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root/'source.plist'
            data = dict(PROJECT_ID='ringout-prod', BUNDLE_ID=verify_ios_firebase.BUNDLE_ID,
                        GOOGLE_APP_ID='prod-app', GCM_SENDER_ID='prod-sender')
            source.write_bytes(plistlib.dumps(data))
            (root/'GoogleService-Info.plist').write_bytes(plistlib.dumps(data))
            (root/'Info.plist').write_bytes(plistlib.dumps({'CFBundleIdentifier': verify_ios_firebase.BUNDLE_ID}))
            verify_ios_firebase.verify(source, root, production=True)
            with self.assertRaises(ValueError):
                verify_ios_firebase.verify(source, root)
            source.write_bytes(plistlib.dumps(dict(data, PROJECT_ID='ringout-8abf2')))
            with self.assertRaises(ValueError):
                verify_ios_firebase.verify(source, root, production=True)

    def test_운영_업로드는_초안을_만들고_기존_심사_보호_옵션으로_저장한다(self):
        self.check_publish()

    def test_동일_AAB_재실행은_재업로드를_생략한다(self):
        self.check_publish(existing=True)

    def test_같은_버전의_다른_AAB는_덮어쓰지_않는다(self):
        with self.assertRaises(internal.CDError):
            self.check_publish(existing=True, mismatch=True)

    def test_심사중_commit_거부시_보호옵션을_제거하거나_재시도하지_않는다(self):
        self.check_publish(commit_error=True)

    def check_publish(self, existing=False, mismatch=False, commit_error=False):
        with tempfile.TemporaryDirectory() as directory:
            aab = Path(directory)/'app.aab'
            aab.write_bytes(b'content')
            sha = hashlib.sha256(b'content').hexdigest()
            calls = []
            def request(url, token, method='GET', body=None, *args, **kwargs):
                calls.append((url, method, body))
                if ':commit?' in url and commit_error:
                    raise internal.CDError('Changes already in review')
                if url.endswith('/edits') or ':commit?' in url:
                    return {'id': 'edit1'}
                if url.endswith('/tracks/production'):
                    return {'releases': [{'status': 'completed', 'versionCodes': ['9']}]}
                if url.endswith('/bundles'):
                    return {'bundles': [{'versionCode': 11, 'sha256': 'wrong' if mismatch else sha}] if existing else []}
                return {'versionCode': 11, 'sha256': sha}
            env = {'GITHUB_REF': 'refs/heads/main', 'GITHUB_EVENT_NAME': 'push', 'GOOGLE_PLAY_ACCESS_TOKEN': 'test',
                   'VERIFIED_AAB_PATH': str(aab), 'VERIFIED_VERSION_CODE': '11', 'GITHUB_SHA': 'a'*40}
            with patch.dict(os.environ, env), patch.object(production, 'request_json', side_effect=request):
                if commit_error:
                    with self.assertRaisesRegex(internal.CDError, 'Changes already in review'):
                        production.publish()
                else:
                    production.publish()
            update = next(call for call in calls if call[1] == 'PUT')
            self.assertEqual(update[2]['releases'][-1]['status'], 'draft')
            self.assertTrue(calls[-1][0].endswith(':commit?changesInReviewBehavior=ERROR_IF_IN_REVIEW'))
            self.assertIsNone(calls[-1][2])
            self.assertEqual(sum(':commit' in call[0] for call in calls), 1)
            self.assertEqual(sum('/bundles?' in call[0] for call in calls), 0 if existing else 1)


if __name__ == '__main__':
    unittest.main()
