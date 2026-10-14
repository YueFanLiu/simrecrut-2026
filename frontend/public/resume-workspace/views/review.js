// Coordinate extraction status, evidence review and confirmed cloud uploads.
function tab(name) {
  document
    .querySelectorAll("[data-tab]")
    .forEach((b) => b.classList.toggle("active", b.dataset.tab === name));
  $("#sourceTab").classList.toggle("hidden", name !== "source");
  $("#factsTab").classList.toggle("hidden", name !== "facts");
}
document
  .querySelectorAll("[data-tab]")
  .forEach((b) => (b.onclick = () => tab(b.dataset.tab)));
function setCurrent(row) {
  current = row;
  $("#reviewTitle").textContent = row.filename;
  $("#reviewMeta").textContent =
    `${labels[row.status]} · Local version ${row.revision} · ID ${row.id.slice(0, 12)}`;
  $("#sourceText").value = row.redacted_text;
  $("#jsonEditor").value = JSON.stringify(row.draft, null, 2);
  $("#factsChecked").checked = !!row.confirmed;
  renderFacts(row.draft);
  $("#uploadCloud").disabled = !row.confirmed || row.status === "PROCESSING";
  $("#cleanButton").disabled = row.status === "PROCESSING";
}
const originalSetCurrent = setCurrent;
setCurrent = function (row) {
  originalSetCurrent(row);
  const available = row.originalAvailable;
  const visual = available && /^\.(pdf|png|jpg|jpeg|webp)$/.test(row.extension);
  $("#cleanButton").disabled = !available || row.status === "PROCESSING";
  $("#retryCleanup").classList.toggle(
    "hidden",
    !available ||
      row.temporary_original !== 1 ||
      row.cloud_revision !== row.revision,
  );
  if (!available)
    $("#sourceText").value =
      "The temporary original was deleted after successful cloud upload. Confirmed structured data remains available.";
  $("#sourceText").classList.toggle("hidden", visual);
  $("#filePreview").replaceChildren();
  $("#filePreview").classList.toggle("hidden", !visual);
  $("#sourceExplainer").textContent = visual
    ? "Embedded PDF text is extracted locally. Scanned PDFs and images need manual entry; OCR is not enabled. No resume content is sent to an AI provider."
    : "DOCX / TXT files are read locally. Review the original and extracted facts.";
  if (visual) {
    for (let n = 1; n <= (row.pages || 1); n++) {
      const block = el("div");
      block.append(el("small", "Page " + n));
      const image = el("img");
      image.src = `/api/v1/resumes/${row.id}/page/${n}?token=${encodeURIComponent(token)}`;
      image.alt = "Resume page " + n;
      image.loading = "lazy";
      block.append(image);
      $("#filePreview").append(block);
    }
  }
};
async function openResume(id) {
  const row = await api("/api/v1/resumes/" + id);
  setCurrent(row);
  $("#sourceChecked").checked = false;
  message("#reviewMessage", row.error || "", !!row.error);
  tab(row.status === "IMPORTED" ? "source" : "facts");
  if (!$("#reviewDialog").open) $("#reviewDialog").showModal();
  if (row.status === "PROCESSING") startPoll();
}
function renderFacts(draft) {
  $("#factCards").replaceChildren();
  for (const [key, name] of Object.entries(groupLabels)) {
    const items = draft.professional[key] || [];
    const card = el("section", undefined, "fact-card");
    card.append(el("h3", `${name} · ${items.length}`));
    if (!items.length) card.append(el("p", "No facts yet", "none"));
    for (const item of items) {
      const n = el("div", undefined, "item");
      let title =
        key === "skills"
          ? item.skillCode
          : key === "experience"
            ? `${item.roleFamilyCode} · ${item.startMonth || "?"} → ${item.isCurrent ? "Present" : item.endMonth || "?"}`
            : key === "education"
              ? `${item.degreeLevelCode} / ${item.subjectCode}`
              : key === "languages"
                ? `${item.languageCode} · ${item.levelCode}`
                : item.projectId;
      n.append(el("strong", title));
      if (item.evidence?.text)
        n.append(
          el(
            "p",
            `“${item.evidence.text}”${item.evidence.page ? " · Page " + item.evidence.page : ""}`,
          ),
        );
      card.append(n);
    }
    $("#factCards").append(card);
  }
  const warnings = draft.warnings || [];
  $("#warnings").textContent = warnings.map((w) => "• " + w).join("\n");
  $("#warnings").classList.toggle("hidden", !warnings.length);
}
$("#jsonEditor").oninput = () => {
  $("#uploadCloud").disabled = true;
  $("#factsChecked").checked = false;
  try {
    renderFacts(JSON.parse($("#jsonEditor").value));
  } catch {}
};
$("#manualReview").onclick = () => {
  tab("facts");
  message(
    "#reviewMessage",
    "Use Add in each category to enter or correct professional facts. No API key is needed.",
  );
};
$("#cleanButton").onclick = (e) =>
  action(e.currentTarget, async () => {
    await api(`/api/v1/resumes/${current.id}/clean`, {});
    current.status = "PROCESSING";
    message(
      "#reviewMessage",
      "Extracting professional facts locally. No AI API is used.",
    );
    startPoll();
    await refresh();
  });
function startPoll() {
  clearInterval(polling);
  polling = setInterval(async () => {
    try {
      if (!$("#reviewDialog").open) {
        clearInterval(polling);
        return;
      }
      const row = await api("/api/v1/resumes/" + current.id);
      if (row.status !== "PROCESSING") {
        clearInterval(polling);
        setCurrent(row);
        tab("facts");
        message(
          "#reviewMessage",
          row.error ||
            "Cleaning complete. Review the facts and supporting evidence.",
          !!row.error,
        );
        await refresh();
      }
    } catch (e) {
      clearInterval(polling);
      message("#reviewMessage", e.message, true);
    }
  }, 2000);
}
$("#reviewDialog").addEventListener("close", () => clearInterval(polling));
async function saveDraft(confirmed) {
  if (confirmed && !$("#factsChecked").checked)
    throw new Error(
      "Review the facts and evidence, then check the confirmation box.",
    );
  let draft;
  try {
    draft = JSON.parse($("#jsonEditor").value);
  } catch {
    throw new Error("Invalid JSON. Check commas, quotes, and brackets.");
  }
  await api(`/api/v1/resumes/${current.id}/save`, { draft, confirmed });
  setCurrent(await api("/api/v1/resumes/" + current.id));
  await refresh();
  message(
    "#reviewMessage",
    confirmed
      ? "Reviewed version saved. Upload to the cloud to complete confirmation and remove the temporary original."
      : "Draft saved locally.",
  );
}
$("#saveDraft").onclick = (e) =>
  action(e.currentTarget, () => saveDraft(false));
$("#confirmDraft").onclick = (e) =>
  action(e.currentTarget, () => saveDraft(true));
$("#uploadCloud").onclick = (e) =>
  action(e.currentTarget, async () => {
    await saveDraft(true);
    message(
      "#reviewMessage",
      "Uploading confirmed JSON. The temporary original is kept until upload succeeds…",
    );
    const r = await api(`/api/v1/resumes/${current.id}/upload`, {});
    setCurrent(await api("/api/v1/resumes/" + current.id));
    message(
      "#reviewMessage",
      r.originalRemoved
        ? "Confirmed JSON uploaded. The temporary original and cached source text were deleted."
        : current.temporary_original === 1
          ? "Cloud upload succeeded. File cleanup is pending; use Retry file cleanup."
          : "Cloud upload succeeded. This existing library original is retained.",
    );
    await refresh();
  });
$("#retryCleanup").onclick = (e) =>
  action(e.currentTarget, async () => {
    const r = await api(`/api/v1/resumes/${current.id}/cleanup`, {});
    setCurrent(await api("/api/v1/resumes/" + current.id));
    message(
      "#reviewMessage",
      r.originalRemoved
        ? "Temporary original deleted."
        : "Cleanup is still pending.",
      !r.originalRemoved,
    );
    await refresh();
  });
