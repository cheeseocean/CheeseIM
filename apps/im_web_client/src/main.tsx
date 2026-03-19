import React from 'react';
import ReactDOM from 'react-dom/client';

import { App } from './app/App';
import './app/styles.css';

const rootElement = document.getElementById('root');

if (rootElement) {
  ReactDOM.createRoot(rootElement).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>
  );
}
