export const factGroups = [
  { key: 'skills', label: 'Skills', singular: 'Skill', fields: [{ key: 'name', label: 'Skill', max: 80 }] },
  { key: 'experience', label: 'Experience', singular: 'Experience', fields: [
    { key: 'role', label: 'Role', max: 100 },
    { key: 'months', label: 'Relevant Months', type: 'number', max: 600 },
    { key: 'summary', label: 'Experience Summary', max: 300 },
  ] },
  { key: 'education', label: 'Education', singular: 'Education', fields: [
    { key: 'degree', label: 'Degree Level', options: ['Secondary', 'Vocational', 'Bachelor', 'Master', 'Doctorate'] },
    { key: 'subject', label: 'Field of Study', max: 100 },
  ] },
  { key: 'languages', label: 'Languages', singular: 'Language', fields: [
    { key: 'name', label: 'Language', max: 80 },
    { key: 'level', label: 'Language Level', options: ['A1', 'A2', 'B1', 'B2', 'C1', 'C2', 'Native'] },
  ] },
  { key: 'projects', label: 'Projects', singular: 'Project', fields: [
    { key: 'title', label: 'Project Title', max: 100 },
    { key: 'summary', label: 'Project Summary', max: 300 },
  ] },
]

// Profile groups hold identity and other CV content for recruiters; they stay outside the professional block.
export const profileGroups = [
  { key: 'personal', label: 'Personal information', singular: 'Personal detail',
    hint: 'Check your name, contact details and photo. Recruiters see these reviewed values instead of your PDF.', fields: [
    { key: 'photo', label: 'Photo', type: 'image' },
    { key: 'fullName', label: 'Full name', max: 120 },
    { key: 'gender', label: 'Gender', options: ['Woman', 'Man', 'Not stated'] },
    { key: 'email', label: 'Email', max: 160 },
    { key: 'phone', label: 'Phone', max: 40 },
    { key: 'city', label: 'City', max: 100 },
  ] },
  { key: 'other', label: 'Other information', singular: 'Other item',
    hint: 'Interests, volunteering, certifications, awards and anything else from your CV.', fields: [
    { key: 'category', label: 'Category', options: ['Interests', 'Volunteering', 'Certifications', 'Awards', 'Other'] },
    { key: 'summary', label: 'Details', max: 300 },
  ] },
]

// Neutral sample portrait drawn inline; no real person is shown.
const samplePhoto = 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 96 96">'
  + '<rect width="96" height="96" rx="14" fill="#dde6f6"/><circle cx="48" cy="38" r="17" fill="#4a78c9"/>'
  + '<path d="M18 88a30 30 0 0 1 60 0z" fill="#1f4fa8"/></svg>')

export const researchFields = [
  { key: 'schoolPrestige', label: 'School Category' }, { key: 'referral', label: 'Referral' },
  { key: 'gender', label: 'Gender' }, { key: 'ethnicity', label: 'Ethnicity' },
]

const observed = (value, excerpt, page = 1) => ({
  value, status: 'OBSERVED', evidenceExcerpt: excerpt, evidenceLocation: `Mock page ${page}`,
})

/** Fixed mock facts and evidence are unrelated to the uploaded PDF. */
export function createReviewDraft(scenario = 'normal') {
  const draft = {
    extractionVersion: 'demo-extraction-1',
    // These groups are required by the demo criteria; Projects is optional.
    requiredGroups: ['skills', 'experience', 'education', 'languages'],
    professional: {
      skills: { confirmedAbsent: false, items: [
        { id: 'skill-java', name: observed('Java', 'Technical skills: Java, Vue and SQL.') },
        { id: 'skill-vue', name: observed('Vue', 'Technical skills: Java, Vue and SQL.') },
        { id: 'skill-sql', name: observed('SQL', 'Technical skills: Java, Vue and SQL.') },
      ] },
      experience: { confirmedAbsent: false, items: [{ id: 'experience-1',
        role: observed('Software Engineer', 'Software Engineer: 24 months developing web applications.'),
        months: observed(24, 'Software Engineer: 24 months developing web applications.'),
        summary: observed('Developed web applications and maintained database integrations.', 'Developed web applications and maintained database integrations.'),
      }] },
      education: { confirmedAbsent: false, items: [{ id: 'education-1',
        degree: observed('Bachelor', 'Bachelor degree in Computer Science.'),
        subject: observed('Computer Science', 'Bachelor degree in Computer Science.'),
      }] },
      languages: { confirmedAbsent: false, items: [{ id: 'language-1',
        name: observed('English', 'English: B2 level.'), level: observed('B2', 'English: B2 level.'),
      }] },
      projects: { confirmedAbsent: false, items: [{ id: 'project-1',
        title: observed('Recruitment Portal', 'Recruitment Portal: built a Vue interface for recruitment workflows.'),
        summary: observed('Built a Vue interface for recruitment workflows.', 'Recruitment Portal: built a Vue interface for recruitment workflows.'),
      }] },
    },
    profile: {
      personal: { confirmedAbsent: false, items: [{ id: 'personal-1',
        photo: observed(samplePhoto, 'Portrait in the CV header.'),
        fullName: observed('Alex Martin', 'Alex Martin, Software Engineer'),
        gender: { value: 'Not stated', status: 'UNKNOWN', evidenceExcerpt: '', evidenceLocation: '' },
        email: observed('alex.martin@example.test', 'alex.martin@example.test · +33 6 00 00 00 00'),
        phone: observed('+33 6 00 00 00 00', 'alex.martin@example.test · +33 6 00 00 00 00'),
        city: observed('Paris, France', 'Based in Paris, France'),
      }] },
      other: { confirmedAbsent: false, items: [
        { id: 'other-1', category: observed('Interests', 'Interests: chess, running.', 2), summary: observed('Chess, running', 'Interests: chess, running.', 2) },
        { id: 'other-2', category: observed('Volunteering', 'Volunteer tutor at a student association.', 2), summary: observed('Volunteer tutor at a student association', 'Volunteer tutor at a student association.', 2) },
      ] },
    },
  }
  if (scenario === 'empty') {
    for (const group of [...Object.values(draft.professional), ...Object.values(draft.profile)]) group.items = []
  }
  if (scenario === 'missing') {
    const field = draft.professional.education.items[0].subject
    field.value = ''
    field.status = 'UNKNOWN'
    field.evidenceExcerpt = ''
    field.evidenceLocation = ''
    draft.professional.languages.items[0].level = {
      value: '', status: 'CONFLICT', evidenceExcerpt: 'English described as fluent; no CEFR level provided.',
      evidenceLocation: 'Mock page 1',
    }
  }
  return draft
}

export function createEmptyFactItem(group) {
  return Object.fromEntries([
    ['id', `user-${globalThis.crypto.randomUUID()}`],
    ...group.fields.map((field) => [field.key, {
      value: '', status: 'USER_SUPPLIED', evidenceExcerpt: '', evidenceLocation: '',
    }]),
  ])
}
