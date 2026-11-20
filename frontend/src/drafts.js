import { request, write } from './identity'

const path = '/content/drafts'

export const drafts = {
  list: (cursor) => request(cursor ? path + '?' + new URLSearchParams({ cursor }) : path),
  get: (id) => request(path + '/' + id),
  create: (input) => write(path, 'POST', input),
  update: (id, input) => write(path + '/' + id, 'PUT', input),
}
