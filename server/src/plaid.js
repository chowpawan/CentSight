import { Configuration, PlaidApi, PlaidEnvironments } from 'plaid';

const env = process.env.PLAID_ENV || 'sandbox';
if (!PlaidEnvironments[env]) throw new Error(`Unknown PLAID_ENV "${env}"`);

export const plaid = new PlaidApi(
  new Configuration({
    basePath: PlaidEnvironments[env],
    baseOptions: {
      headers: {
        'PLAID-CLIENT-ID': process.env.PLAID_CLIENT_ID,
        'PLAID-SECRET': process.env.PLAID_SECRET,
      },
    },
  })
);

export const plaidErrorCode = (err) => err?.response?.data?.error_code;
export const plaidErrorBody = (err) =>
  err?.response?.data || { error_message: err?.message || 'Unknown error' };
