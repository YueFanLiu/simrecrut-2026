// Share DOM helpers, labels and local state across ordered workspace scripts.
const $ = (s) => document.querySelector(s);
const token = $('meta[name="app-token"]').content;
let resumes = [],
  current = null,
  config = {},
  polling = null,
  toastTimer,
  busy = false;
const labels = {
  IMPORTED: "Imported",
  PROCESSING: "Cleaning",
  REVIEW_REQUIRED: "Needs review",
  CONFIRMED: "Confirmed",
  FAILED: "Failed",
};
const colors = {
  IMPORTED: "",
  PROCESSING: "blue",
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
