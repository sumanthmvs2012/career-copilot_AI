// Dev-only stand-in for POST /agent/run. Creates nothing; it only returns plausible counts.
const MOCK_DELAY_MS = 600;

export async function mockRunAgent() {
  await new Promise((resolve) => setTimeout(resolve, MOCK_DELAY_MS));
  return { considered: 12, draftsCreated: 3 };
}
