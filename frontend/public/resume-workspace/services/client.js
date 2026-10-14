// Send authenticated local Java API requests and handle action errors.
async function api(path, body) {
  const r = await fetch(path, {
    method: body === undefined ? "GET" : "POST",
    headers: { "X-App-Token": token, "Content-Type": "application/json" },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  const data = await r.json();
  if (!r.ok || data.code !== 0)
    throw new Error(data.message || "Request failed");
  return data.data;
}
async function action(button, fn) {
  if (button.disabled) return;
  button.disabled = true;
  try {
    await fn();
  } catch (e) {
    toast(e.message, true);
    if ($("#reviewDialog").open) message("#reviewMessage", e.message, true);
    if ($("#settingsDialog").open) message("#settingsMessage", e.message, true);
  } finally {
    button.disabled = false;
  }
}
