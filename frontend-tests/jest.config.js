module.exports = {
  testEnvironment: 'jest-environment-jsdom',
  testEnvironmentOptions: {
    // Allows <script> elements appended to the document to actually execute
    runScripts: 'dangerously',
    url: 'http://localhost:8080'
  },
  testMatch: ['**/unit/**/*.test.js'],
  setupFilesAfterEnv: ['./unit/setup.js']
};
