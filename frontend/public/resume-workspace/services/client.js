// Send authenticated local Java API requests and handle action errors.
async function api(path, body, method) {
  try {
    const r = await fetch(path, {
      method: method || (body === undefined ? "GET" : "POST"),
      headers: { "X-App-Token": token, "Content-Type": "application/json" },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    });
    const data = await r.json();
    if (!r.ok || data.code !== 0)
      throw new Error(uiMessage(data.message || data.msg));
    return data.data;
  } catch (error) {
    if (error instanceof SyntaxError) throw new Error("The service returned an invalid response. Please try again.");
    throw new Error(uiMessage(error.message, "The service is unavailable. Please try again."));
  }
}
async function action(button, fn) {
  if (button.disabled) return;
  button.disabled = true;
  try {
    await fn();
  } catch (e) {
    const text = uiMessage(e.message);
    toast(text, true);
    if ($("#reviewDialog").open) message("#reviewMessage", text, true);
    if ($("#settingsDialog").open) message("#settingsMessage", text, true);
  } finally {
    button.disabled = false;
  }
}
