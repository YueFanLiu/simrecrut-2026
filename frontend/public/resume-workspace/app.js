// Initialize domain modules after all ordered deferred scripts have loaded.
Promise.all([loadSettings(), refresh()]).catch((e) => toast(e.message, true));

$("#factsChecked").onchange = () => {
  $("#uploadCloud").disabled = !$("#factsChecked").checked;
};

// Keep the upload summary separate from the fact-verification screen.
const styledSetCurrent = setCurrent;
setCurrent = function (row) {
  styledSetCurrent(row);
  $("#reviewTitle").textContent = "Verify Your Information";
  const summary = $("#extractionSummary");
  summary.replaceChildren();
  summary.classList.remove("preview-empty");
  summary.classList.add("preview-summary");
  summary.append(
    el("h3", row.filename),
    el("p", labels[row.status] || row.status),
  );
  for (const [key, name] of Object.entries(groupLabels)) {
    const line = el("div", undefined, "preview-count");
    line.append(
      el("span", name),
      el("strong", String(row.draft?.professional?.[key]?.length || 0)),
    );
    summary.append(line);
  }
  summary.append(
    el("small", "Preview only. Verify facts and evidence before confirmation."),
  );
};
