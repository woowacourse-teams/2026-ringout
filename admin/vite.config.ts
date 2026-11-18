import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_');
  const proxy = {
    // Keep the full /api path; the backend also maps /api/v1/... routes.
    '/api': {
      target: env.VITE_API_BASE_URL,
      changeOrigin: true,
    },
  };
  return {
    plugins: [react(), {
      name: 'local-admin-origin',
      configureServer(server) {
        if (env.VITE_APP_ENV !== 'local') return;
        server.middlewares.use((request, response, next) => {
          // OAuth state and the session cookie must use the callback's host.
          const isDocument = request.method === 'GET'
            && request.headers.accept?.includes('text/html');
          if (isDocument && request.headers.host?.split(':')[0] === 'localhost') {
            const port = server.config.server.port ?? 5173;
            response.writeHead(302, {
              Location: `http://127.0.0.1:${port}${request.url ?? '/'}`,
              'Cache-Control': 'no-store',
            });
            response.end();
            return;
          }
          next();
        });
      },
    }],
    base: './',
    server: { proxy },
    preview: { proxy },
  };
});
