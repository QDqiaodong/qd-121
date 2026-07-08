export function downloadBlob(blob, filename) {
  const url = window.URL.createObjectURL(new Blob([blob]))
  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', filename)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

export function getFileNameFromDisposition(disposition) {
  if (!disposition) return 'download'
  const matches = /filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/.exec(disposition)
  let filename = matches && matches[1]
  if (filename) {
    filename = filename.replace(/['"]/g, '')
    if (filename.includes("UTF-8''")) {
      filename = decodeURIComponent(filename.replace("UTF-8''", ''))
    }
  }
  return filename || 'download'
}
