// Render the CV library and apply filename searches to loaded records.
async function refresh() {
  resumes = await api("/api/v1/resumes");
  renderList();
}
function renderList() {
  const q = $("#search").value.toLowerCase();
  const shown = resumes.filter((r) => r.filename.toLowerCase().includes(q));
  $("#totalCount").textContent = resumes.length;
  $("#reviewCount").textContent = resumes.filter(
    (r) => r.status === "REVIEW_REQUIRED",
  ).length;
  $("#cloudCount").textContent = resumes.filter(
    (r) => r.cloud_revision === r.revision,
  ).length;
  $("#listCount").textContent = resumes.length;
  $("#emptyState").classList.toggle("hidden", shown.length > 0);
  $("#emptyState h3").textContent = q
    ? "No matching files"
    : "Start with your first resume";
  $("#resumeRows").replaceChildren();
  for (const r of shown) {
    const tr = el("tr");
    const fileCell = el("td");
    const wrap = el("div", undefined, "file-cell");
    wrap.append(
      el("span", r.filename.split(".").pop().toUpperCase(), "file-icon"),
    );
    const detail = el("div");
    detail.append(
      el("span", r.filename),
      el(
        "small",
        `${(r.bytes / 1024).toFixed(0)} KB${r.pages ? " · " + r.pages + " pages" : ""} · ${r.originalAvailable ? "Temporary original available" : "Original removed"}`,
      ),
    );
    wrap.append(detail);
    fileCell.append(wrap);
    tr.append(
      fileCell,
      el(
        "td",
        new Date(r.created_at).toLocaleString("en-GB", {
          month: "2-digit",
          day: "2-digit",
          hour: "2-digit",
          minute: "2-digit",
        }),
      ),
    );
    const st = el("td");
    st.append(
      el(
        "span",
        labels[r.status] || r.status,
        "status " + (colors[r.status] || ""),
      ),
    );
    tr.append(st);
    const cloud = el("td");
    cloud.append(
      el(
        "span",
        r.cloud_revision === r.revision
          ? "Synced"
          : r.cloud_revision
            ? "New version to sync"
            : "Not uploaded",
        "status " + (r.cloud_revision === r.revision ? "green" : ""),
      ),
    );
    tr.append(cloud);
    const td = el("td");
    const b = el("button", "Open →", "row-open");
    b.onclick = () => action(b, () => openResume(r.id));
    td.append(b);
    tr.append(td);
    $("#resumeRows").append(tr);
  }
}
$("#search").oninput = renderList;
$("#refresh").onclick = (e) => action(e.currentTarget, refresh);
