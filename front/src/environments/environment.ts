export const environment = {
  apiBaseUrl: '/api/v1',
  logto: {
    endpoint: 'https://logto.arisma.com.co',
    appId: 'wpqq5ute5og9jfags2mom',
  },
  /** Shown on the login page as one-click shortcuts; matches the backend seed (SEED_DEMO_USERS). */
  demoAccounts: [
    { label: 'Usuario', email: 'usuario@test.com' },
    { label: 'Admin', email: 'admin@test.com' },
    { label: 'Super admin', email: 'superadmin@test.com' },
  ],
};
