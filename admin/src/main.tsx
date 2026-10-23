import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import { adminConfig } from './config';
import './styles.css';
document.title = `[${adminConfig.label}] 링아웃 관리자`;

ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode>
  <App />
</React.StrictMode>);
