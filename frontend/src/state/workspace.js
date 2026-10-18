// Coordinate session state and demo actions consumed by independent page components.
import { ref, reactive, computed, watch, inject } from "vue";
import { useRouter, useRoute } from "vue-router";
import { pages } from "../router/pages.js";
import { demoMode } from "../store/modules/user";
import { fixtures } from "../api/demoFixtures.js";

export const workspaceKey = Symbol("workspace");
export function useWorkspace() {
  const workspace = inject(workspaceKey);
  if (!workspace) throw new Error("Workspace provider is missing");
  return workspace;
}

// Session-only demo state. Page components share this provider, not global singletons.
export function createWorkspace() {
  const router = useRouter();
  const route = useRoute();
  const path = computed(() => route.fullPath);
  const page = computed(
    () =>
      pages.find((p) => p[3] === path.value) ||
      pages.find(
        (p) =>
          p[0] ===
          (path.value.startsWith("/candidate/resumes/")
            ? "C04"
            : path.value.startsWith("/research/experiments/")
              ? "R03"
              : path.value.startsWith("/reviewer/tasks/")
                ? "V02"
                : path.value.startsWith("/research/metrics")
                  ? "R06"
                  : "R01"),
      ),
  );
  const data = reactive(
    JSON.parse(
      JSON.stringify(
        demoMode
          ? fixtures
          : Object.fromEntries(
              Object.keys(fixtures).map((key) => [
                key,
                Array.isArray(fixtures[key]) ? [] : fixtures[key],
              ]),
            ),
      ),
    ),
  );
  const search = ref(""),
    status = ref("All statuses"),
    source = ref("Reviewed Profiles");
  const tab = ref("Configuration"),
    selected = ref([]),
    toast = ref(""),
    drawer = ref(null);
  const menu = ref(false),
    decision = ref(""),
    reason = ref(""),
    confirm = ref(false);
  const error = ref(""),
    role = ref("Researcher");
  const rolePermissions = reactive({
    Candidate: ["Candidate workspace"],
    HR: ["HR workspace"],
    Researcher: ["Research workspace", "View experiments"],
    Admin: ["Administration"],
  });
  const form = reactive({
    name: "",
    type: "",
    job: "",
    weight: "",
    protocol: "",
    neutral: "",
    biased: "",
  });
  const experiment = computed(() =>
    data.experiments.find(
      (e) => e.id === path.value.split("?")[0].split("/").pop(),
    ),
  );
  const task = computed(() =>
    data.tasks.find((t) => t.id === path.value.split("?")[0].split("/").pop()),
  );
  const missingRecord = computed(
    () =>
      (page.value[0] === "R03" && !experiment.value) ||
      (page.value[0] === "V02" && !task.value),
  );
  const list = computed(
    () =>
      ({
        R01: data.experiments,
        R04: data.subjects,
        R05: data.models,
        V01: data.tasks,
        A01: data.users,
        A03: data.models,
        A06: data.failures,
        A07: data.audits,
      })[page.value[0]] || [],
  );
  const statuses = computed(() => [
    ...new Set(list.value.map((r) => r.status)),
  ]);
  const rows = computed(() =>
    list.value.filter(
      (r) =>
        (page.value[0] !== "R04" || r.source === source.value) &&
        (status.value === "All statuses" || r.status === status.value) &&
        Object.values(r)
          .join(" ")
          .toLowerCase()
          .includes(search.value.toLowerCase()),
    ),
  );
  let timer;
  function notify(text) {
    toast.value = text;
    clearTimeout(timer);
    timer = setTimeout(() => (toast.value = ""), 5000);
  }
  function go(url) {
    router.push(url);
    search.value = "";
    status.value = "All statuses";
    tab.value = "Configuration";
    drawer.value = null;
    menu.value = false;
    error.value = "";
    decision.value = "";
    reason.value = "";
    confirm.value = false;
    selected.value = [...(experiment.value?.subjectIds || [])];
    window.scrollTo(0, 0);
  }
  const pop = () => {
    drawer.value = null;
    tab.value = "Configuration";
    decision.value = "";
    reason.value = "";
    confirm.value = false;
    error.value = "";
    menu.value = false;
    search.value = "";
    status.value = "All statuses";
    selected.value = [...(experiment.value?.subjectIds || [])];
  };
  watch(() => route.fullPath, pop);
  function create() {
    if (!demoMode)
      return notify(
        "This research action is not connected to a production API.",
      );
    if (Object.values(form).some((v) => !v.trim())) {
      error.value = "Complete all seven required fields.";
      return;
    }
    const id = "EXP-" + String(data.experiments.length + 1).padStart(3, "0");
    data.experiments.unshift({
      ...form,
      id,
      name: form.name.trim(),
      subjects: 0,
      status: "Draft",
      date: new Date().toISOString().slice(0, 10),
    });
    selected.value = [];
    go("/research/experiments/" + id);
    notify(
      "Demo draft created. Select subjects next; no execution was started.",
    );
  }
  function select(id) {
    selected.value = selected.value.includes(id)
      ? selected.value.filter((v) => v !== id)
      : [...selected.value, id];
  }
  function submit() {
    if (!demoMode)
      return notify(
        "This research action is not connected to a production API.",
      );
    if (!task.value || task.value.status === "Submitted") return;
    if (
      !decision.value ||
      !reason.value.trim() ||
      reason.value.trim().length > 2000 ||
      !confirm.value
    ) {
      error.value =
        "Choose a decision, add a 1–2000 character reason and confirm submission.";
      return;
    }
    Object.assign(task.value, {
      status: "Submitted",
      decision: decision.value,
      reason: reason.value.trim(),
    });
    error.value = "";
    notify("Demo review submitted and locked for this session.");
  }
  function release(model) {
    drawer.value = {
      kind: "release",
      item: model,
      next: { Tested: "Approved", Approved: "Active", Active: "Retired" }[
        model.status
      ],
    };
  }
  function apply() {
    if (!demoMode)
      return notify(
        "This research action is not connected to a production API.",
      );
    const d = drawer.value;
    if (d.kind === "release") {
      if (d.next === "Active")
        data.models
          .filter((m) => m.name === d.item.name && m.status === "Active")
          .forEach((m) => (m.status = "Retired"));
      d.item.status = d.next;
    } else if (d.kind === "retry") d.item.status = "Queued";
    else if (d.kind === "user") {
      d.item.role = role.value;
    }
    drawer.value = null;
    notify("Demo change applied to this session only.");
  }
  const badge = (value) => value.toLowerCase().replaceAll(" ", "-");
  return {
    pages,
    page,
    path,
    data,
    search,
    status,
    statuses,
    source,
    tab,
    selected,
    toast,
    drawer,
    menu,
    decision,
    reason,
    confirm,
    error,
    role,
    rolePermissions,
    form,
    experiment,
    missingRecord,
    task,
    rows,
    go,
    notify,
    create,
    select,
    submit,
    release,
    apply,
    badge,
  };
}
