export const maximumCvBytes = 10 * 1024 * 1024

/** Inspect the local PDF only; no bytes or extracted text are sent to a service. */
export async function validateCvFile(file) {
  if (!file || !/\.pdf$/i.test(file.name) ||
      (file.type && !['application/pdf', 'application/octet-stream'].includes(file.type))) {
    throw new Error('Choose one PDF file. Other file formats are not supported.')
  }
  if (!file.size) throw new Error('The PDF is empty. Choose a different file.')
  if (file.size > maximumCvBytes) throw new Error('The PDF must be 10 MB or smaller.')
  try {
    const { PDFDocument } = await import('pdf-lib')
    let document
    try {
      document = await PDFDocument.load(await file.arrayBuffer(), {
        ignoreEncryption: true, updateMetadata: false, throwOnInvalidObject: true,
      })
    } catch (error) {
      throw new Error('This file could not be read as a PDF. Choose a valid PDF.')
    }
    // Inspect the encryption flag only; never attempt to read encrypted pages.
    if (document.isEncrypted) {
      throw new Error('Encrypted or password-protected PDFs are not supported. Choose an unencrypted PDF.')
    }
    const pageCount = document.getPageCount()
    if (!pageCount) throw new Error('The PDF must contain at least one page.')
    if (pageCount > 20) throw new Error('The PDF must contain no more than 20 pages.')
    return { name: file.name, size: file.size, pageCount }
  } catch (error) {
    throw new Error(error.message || 'Unable to check the PDF. Please choose it again.')
  }
}

export function formatCvSize(bytes) {
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
}
