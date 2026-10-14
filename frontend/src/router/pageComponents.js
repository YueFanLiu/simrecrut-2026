// Map each page identifier to its independently maintained Vue component.
import ExperimentList from "../views/research/ExperimentList.vue";
import ExperimentCreate from "../views/research/ExperimentCreate.vue";
import ExperimentDetails from "../views/research/ExperimentDetails.vue";
import ResearchSubjects from "../views/research/ResearchSubjects.vue";
import ResearchModels from "../views/research/ResearchModels.vue";
import ResearchMetrics from "../views/research/ResearchMetrics.vue";
import ReviewTasks from "../views/reviewer/ReviewTasks.vue";
import ReviewTaskDetails from "../views/reviewer/ReviewTaskDetails.vue";
import AdminUsers from "../views/admin/AdminUsers.vue";
import AdminRoles from "../views/admin/AdminRoles.vue";
import ModelReleases from "../views/admin/ModelReleases.vue";
import FrozenProtocols from "../views/admin/FrozenProtocols.vue";
import ServiceHealth from "../views/admin/ServiceHealth.vue";
import ProcessingFailures from "../views/admin/ProcessingFailures.vue";
import AuditLogs from "../views/admin/AuditLogs.vue";

export const pageComponents = {
  R01: ExperimentList,
  R02: ExperimentCreate,
  R03: ExperimentDetails,
  R04: ResearchSubjects,
  R05: ResearchModels,
  R06: ResearchMetrics,
  V01: ReviewTasks,
  V02: ReviewTaskDetails,
  A01: AdminUsers,
  A02: AdminRoles,
  A03: ModelReleases,
  A04: FrozenProtocols,
  A05: ServiceHealth,
  A06: ProcessingFailures,
  A07: AuditLogs,
};
