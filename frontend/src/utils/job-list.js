/** Apply demo filters and return a new, sorted array without changing the source data. */
export function filterPublishedJobs(jobs, filters) {
  const keyword = filters.keyword.trim().toLowerCase()
  const filtered = jobs.filter((job) => {
    const searchableText = [job.title, job.company, job.summary].join(' ').toLowerCase()
    return (!keyword || searchableText.includes(keyword)) &&
      (!filters.category || job.category === filters.category) &&
      (!filters.location || job.location === filters.location) &&
      (!filters.employmentType || job.employmentType === filters.employmentType)
  })

  return filtered.sort((first, second) => {
    if (filters.sort === 'title') return first.title.localeCompare(second.title, 'en')
    const dates = first.publishedAt.localeCompare(second.publishedAt)
    return (filters.sort === 'oldest' ? dates : -dates) || first.title.localeCompare(second.title, 'en')
  })
}

export function formatPublishedDate(value) {
  return new Intl.DateTimeFormat('en-GB', {
    day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC',
  }).format(new Date(`${value}T00:00:00Z`))
}
