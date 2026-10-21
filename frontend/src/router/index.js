import { createRouter, createWebHistory } from 'vue-router'
import { pinia } from '../store/index.js'
import { demoMode } from '../config/runtime.js'
import { useAuthStore } from '../store/modules/auth.js'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/forgot-password', name: 'ForgotPassword', component: () => import('../views/auth/ForgotPasswordView.vue'), meta: { title: 'Reset password' } },
    // Keep the existing resume API adapter alongside the preferred prototype pages.
    { path: '/candidate/resumes', name: 'ConnectedResumes', component: () => import('../views/candidate/ResumeUpload.vue'), meta: { title: 'My CVs', requiresDemoSession: true, demoRole: 'Candidate', connected: true } },
    { path: '/candidate/resumes/:id', name: 'ConnectedResumeReview', component: () => import('../views/candidate/ResumeReview.vue'), meta: { title: 'Review CV facts', requiresDemoSession: true, demoRole: 'Candidate', connected: true } },
    {
      path: '/login',
      name: 'Login',
      component: () => import('../views/auth/LoginView.vue'),
      meta: { title: 'Sign in' },
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('../views/auth/RegisterView.vue'),
      meta: { title: 'Create an account' },
    },
    {
      path: '/403',
      name: 'Forbidden',
      component: () => import('../views/error/ForbiddenView.vue'),
      meta: { title: 'Access denied', requiresDemoSession: true },
    },
    {
      path: '/dashboard',
      name: 'DemoDashboard',
      component: () => import('../views/DemoDashboardView.vue'),
      meta: { title: 'Demo dashboard', requiresDemoSession: true },
    },
    { path: '/admin/audit', name: 'AdminAudit',
      component: () => import('../views/admin/audit/index.vue'),
      meta: { title: 'Audit Logs', requiresDemoSession: true, demoRole: 'Admin' } },
    { path: '/admin/failures', name: 'AdminFailures',
      component: () => import('../views/admin/failures/index.vue'),
      meta: { title: 'Failed Jobs and Errors', requiresDemoSession: true, demoRole: 'Admin' } },
    { path: '/admin/system-health', name: 'AdminHealth',
      component: () => import('../views/admin/system-health/index.vue'),
      meta: { title: 'Service Health', requiresDemoSession: true, demoRole: 'Admin' } },
    { path: '/admin/protocols', name: 'AdminProtocols',
      component: () => import('../views/admin/protocols/index.vue'),
      meta: { title: 'Frozen Protocols', requiresDemoSession: true, demoRole: 'Admin' } },
    { path: '/admin/models', name: 'AdminModels',
      component: () => import('../views/admin/models/index.vue'),
      meta: { title: 'Model Versions and Release', requiresDemoSession: true, demoRole: 'Admin' } },
    { path: '/admin/roles', name: 'AdminRoles',
      component: () => import('../views/admin/roles/index.vue'),
      meta: { title: 'Roles and Permissions', requiresDemoSession: true, demoRole: 'Admin' } },
    { path: '/admin/users', name: 'AdminUsers',
      component: () => import('../views/admin/users/index.vue'),
      meta: { title: 'User Management', requiresDemoSession: true, demoRole: 'Admin' } },
    { path: '/reviewer/tasks', name: 'MyReviewTasks',
      component: () => import('../views/reviewer/tasks/index.vue'),
      meta: { title: 'My Review Tasks', requiresDemoSession: true, demoRoles: ['HR', 'Researcher'] } },
    { path: '/reviewer/tasks/:reviewId', name: 'ReviewTaskDetail',
      component: () => import('../views/reviewer/tasks/detail.vue'),
      meta: { title: 'Review Task Details and Submission', requiresDemoSession: true, demoRoles: ['HR', 'Researcher'] } },
    { path: '/research', component: () => import('../views/research/ResearchWorkspace.vue'),
      meta: { requiresDemoSession: true, demoRole: 'Researcher' }, redirect: '/research/experiments',
      children: [
        { path: 'experiments', name: 'ResearchExperiments', component: () => import('../views/research/ExperimentList.vue'), meta: { title: 'Research Experiments' } },
        { path: 'experiments/new', name: 'ResearchExperimentCreate', component: () => import('../views/research/ExperimentCreate.vue'), meta: { title: 'Create Experiment' } },
        { path: 'experiments/:experimentId', name: 'ResearchExperimentDetail', component: () => import('../views/research/ExperimentDetails.vue'), meta: { title: 'Experiment Details and Comparison' } },
        { path: 'cases', name: 'ResearchSubjects', component: () => import('../views/research/ResearchSubjects.vue'), meta: { title: 'Research Subjects' } },
        { path: 'models', name: 'ResearchModels', component: () => import('../views/research/ResearchModels.vue'), meta: { title: 'Research Models' } },
        { path: 'metrics', name: 'ResearchMetrics', component: () => import('../views/research/ResearchMetrics.vue'), meta: { title: 'Experiment Metrics' } },
      ] },
    {
      path: '/hr/jobs',
      name: 'HrJobs',
      component: () => import('../views/hr/jobs/index.vue'),
      meta: { title: 'Job management', requiresDemoSession: true, demoRole: 'HR' },
    },
    { path: '/hr/results/:caseId', name: 'HrCaseDetail',
      component: () => import('../views/hr/cases/detail.vue'),
      meta: { title: 'Authorised case details', requiresDemoSession: true, demoRole: 'HR' } },
    { path: '/hr/results', name: 'HrCases',
      component: () => import('../views/hr/cases/index.vue'),
      meta: { title: 'Authorised cases', requiresDemoSession: true, demoRole: 'HR' } },
    { path: '/hr/jobs/:jobId/versions', name: 'HrJobVersions',
      component: () => import('../views/hr/jobs/versions.vue'),
      meta: { title: 'Job versions', requiresDemoSession: true, demoRole: 'HR' } },
    ...[
      ['/hr/jobs/new', 'HrJobNew'],
      ['/hr/jobs/:jobId/edit', 'HrJobEdit'],
      ['/hr/jobs/:jobId/requirements', 'HrJobRequirements'],
      ['/hr/jobs/:jobId/weights', 'HrJobWeights'],
    ].map(([path, name]) => ({ path, name, component: () => import('../views/hr/jobs/wizard.vue'),
      meta: { title: 'Job configuration', requiresDemoSession: true, demoRole: 'HR' } })),
    {
      path: '/candidate/jobs',
      name: 'PublishedJobs',
      component: () => import('../views/candidate/jobs/index.vue'),
      meta: { title: 'Published jobs', requiresDemoSession: true, demoRole: 'Candidate' },
    },
    {
      path: '/candidate/jobs/:jobId',
      name: 'JobDetail',
      component: () => import('../views/candidate/jobs/detail.vue'),
      meta: { title: 'Job details', requiresDemoSession: true, demoRole: 'Candidate' },
    },
    {
      path: '/candidate/cases',
      name: 'MySimulations',
      component: () => import('../views/candidate/cases/index.vue'),
      meta: { title: 'My simulations', requiresDemoSession: true, demoRole: 'Candidate' },
    },
    {
      path: '/candidate/cases/:caseId/upload',
      name: 'CvUpload',
      component: () => import('../views/candidate/cases/upload.vue'),
      meta: { title: 'Upload your CV', requiresDemoSession: true, demoRole: 'Candidate' },
    },
    {
      path: '/candidate/cases/:caseId/review',
      name: 'CvReview',
      component: () => import('../views/candidate/cases/review.vue'),
      meta: { title: 'Review your CV facts', requiresDemoSession: true, demoRole: 'Candidate' },
    },
    {
      path: '/candidate/cases/:caseId/progress',
      name: 'SimulationProgress',
      component: () => import('../views/candidate/cases/progress.vue'),
      meta: { title: 'Simulation progress', requiresDemoSession: true, demoRole: 'Candidate' },
    },
    {
      path: '/candidate/cases/:caseId/result',
      name: 'SimulationResults',
      component: () => import('../views/candidate/cases/result.vue'),
      meta: { title: 'Simulation results', requiresDemoSession: true, demoRole: 'Candidate' },
    },
    {
      path: '/404',
      name: 'NotFound',
      component: () => import('../views/error/NotFoundView.vue'),
      meta: { title: 'Page not found' },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'NotFoundFallback',
      component: () => import('../views/error/NotFoundView.vue'),
      meta: { title: 'Page not found' },
    },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

// Server permissions remain authoritative; connected pages never use demo fixtures.
router.beforeEach(async (to) => {
  const auth = useAuthStore(pinia)
  if (!demoMode) {
    try { await auth.restoreConnectedSession() } catch { auth.demoUser = null }
    if (to.meta.requiresDemoSession && !auth.isDemoSignedIn) return { name: 'Login' }
    if (to.meta.demoRole && !auth.hasDemoRole(to.meta.demoRole)) return { name: 'Forbidden' }
    if (to.meta.demoRoles && !to.meta.demoRoles.some(role => auth.hasDemoRole(role))) return { name: 'Forbidden' }
    if (['Login', 'Register'].includes(to.name) && auth.isDemoSignedIn) return { name: 'DemoDashboard' }
    return
  }
  if (to.meta.requiresDemoSession && !auth.isDemoSignedIn) return { name: 'Login' }
  if (to.meta.demoRoles && !to.meta.demoRoles.some(role => auth.hasDemoRole(role))) return { name: 'Forbidden' }
  if (to.meta.demoRole && !auth.hasDemoRole(to.meta.demoRole)) return { name: 'Forbidden' }
  if (['Login', 'Register'].includes(to.name) && auth.isDemoSignedIn) {
    return { name: 'DemoDashboard' }
  }
})

router.afterEach((to, _from, failure) => {
  if (failure) return
  document.title = `${to.meta.title || 'SimRecrut'} | SimRecrut`
})

export default router
