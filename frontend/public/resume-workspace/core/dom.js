// Share DOM helpers, labels and local state across ordered workspace scripts.
const $ = (s) => document.querySelector(s);
function uiMessage(value, fallback = "The request could not be completed. Please try again.") {
  return typeof value === "string" && value.trim() && !/\p{Script=Han}/u.test(value)
    ? value.trim()
    : fallback;
}
// Show our own English validation instead of browser-language tooltips.
function validateForm(form) {
  const invalid = [...form.elements].find((field) => field.willValidate && !field.validity.valid);
  if (!invalid) return true;
  toast("Check the required fields, formats and allowed number ranges.", true);
  invalid.focus();
  return false;
}
const token = $('meta[name="app-token"]').content;
let resumes = [],
  current = null,
  config = {},
  polling = null,
  toastTimer,
  busy = false;
const labels = {
  UPLOADED: "Imported",
  EXTRACTING: "Cleaning",
  REVIEW_REQUIRED: "Needs review",
  CONFIRMED: "Confirmed",
  FAILED: "Failed",
};
const colors = {
  UPLOADED: "",
  EXTRACTING: "blue",
  REVIEW_REQUIRED: "amber",
  CONFIRMED: "green",
  FAILED: "red",
};
const groupLabels = {
  skills: "Skills",
  experience: "Experience",
  education: "Education",
  languages: "Languages",
  projects: "Projects",
};
function el(tag, text, cls) {
  const n = document.createElement(tag);
  if (text !== undefined) n.textContent = text;
  if (cls) n.className = cls;
  return n;
}
function confirmDelete() {
  return new Promise((resolve) => {
    const dialog = el("dialog");
    const title = el("h2", "Delete CV");
    title.id = "deleteCvTitle";
    dialog.setAttribute("aria-labelledby", title.id);
    const actions = el("div", undefined, "dialog-actions");
    const cancel = el("button", "Cancel");
    const confirm = el("button", "Delete CV", "primary");
    let accepted = false;
    cancel.onclick = () => dialog.close();
    confirm.onclick = () => { accepted = true; dialog.close(); };
    dialog.addEventListener("close", () => {
      dialog.remove();
      resolve(accepted);
    }, { once: true });
    actions.append(cancel, confirm);
    dialog.append(title, el("p", "Delete this CV, its original and published JSON? This cannot be undone."), actions);
    document.body.append(dialog);
    dialog.showModal();
    cancel.focus();
  });
}
function message(id, text, isError = false) {
  const n = $(id);
  n.textContent = text;
  n.classList.toggle("hidden", !text);
  n.classList.toggle("error", isError);
}
function toast(text, isError = false) {
  clearTimeout(toastTimer);
  message("#toast", text, isError);
  toastTimer = setTimeout(() => message("#toast", ""), 6500);
}
