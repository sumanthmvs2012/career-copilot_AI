// Copies text and reports whether it worked. Never logs the text itself.
export async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text ?? '');
    return true;
  } catch {
    return false;
  }
}
