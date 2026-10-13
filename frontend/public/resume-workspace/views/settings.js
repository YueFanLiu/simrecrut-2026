// Load and save local database connection settings without exposing saved secrets.
async function loadSettings() {
  config = await api("/api/v1/settings");
  $("#localFolder").textContent = config.localFolder;
  const form = $("#settingsForm");
  for (const key of ["host", "port", "user", "database", "caPem"])
    form.elements[key].value =
      config[key] || { port: "25060", database: "defaultdb" }[key] || "";
  form.elements.password.placeholder = config.hasPassword
    ? "Saved; leave blank to keep"
    : "Not configured";
  $("#connectionBadge").textContent = config.host
    ? "Local parser ready · Aiven configured"
    : "Local parser ready · Set up Aiven";
}
function settingsOpen() {
  message("#settingsMessage", "");
  $("#settingsDialog").showModal();
}
async function saveConfig() {
  const form = $("#settingsForm");
  const body = Object.fromEntries(new FormData(form));
  await api("/api/v1/settings", body);
  form.elements.password.value = "";
  await loadSettings();
  message("#settingsMessage", "Settings encrypted and saved locally.");
}
$("#settingsTop").onclick = settingsOpen;
$("#navSettings").onclick = settingsOpen;
$("#navWorkspace").onclick = () =>
  window.scrollTo({ top: 0, behavior: "smooth" });
document
  .querySelectorAll("[data-close]")
  .forEach((b) => (b.onclick = () => $("#" + b.dataset.close).close()));
$("#settingsForm").onsubmit = (e) => {
  e.preventDefault();
  action(e.submitter, saveConfig);
};
$("#testCloud").onclick = (e) =>
  action(e.currentTarget, async () => {
    await saveConfig();
    const r = await api("/api/v1/cloud/test", {});
    message(
      "#settingsMessage",
      `Connected · MySQL ${r.version} · Database ${r.database}`,
    );
    $("#connectionBadge").textContent = "Local parser ready · Cloud connected";
  });
$("#caFile").onchange = async (e) => {
  const file = e.target.files[0];
  if (file) $("#settingsForm").elements.caPem.value = await file.text();
};
