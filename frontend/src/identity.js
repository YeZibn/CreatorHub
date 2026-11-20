class IdentityError extends Error {
  constructor(status, code) {
    super(code)
    this.status = status
    this.code = code
  }
}

async function request(path, options = {}) {
  let response
  try {
    response = await fetch('/api/v1' + path, {
      credentials: 'same-origin',
      cache: 'no-store',
      signal: AbortSignal.timeout(12_000),
      ...options,
      headers: { Accept: 'application/json', ...options.headers },
    })
  } catch {
    throw new IdentityError(0, 'NETWORK_UNAVAILABLE')
  }

  if (response.status === 204) return undefined
  const payload = await response.json().catch(() => null)
  if (!response.ok) throw new IdentityError(response.status, payload?.code || 'REQUEST_FAILED')
  if (!payload) throw new IdentityError(response.status, 'INVALID_RESPONSE')
  return payload
}

async function write(path, method, body) {
  const { headerName, token } = await request('/auth/csrf')
  const headers = { [headerName]: token }
  const options = { method, headers }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    options.body = JSON.stringify(body)
  }
  return request(path, options)
}

export const identity = {
  current: () => request('/me'),
  register: (body) => write('/auth/register', 'POST', body),
  login: (body) => write('/auth/login', 'POST', body),
  logout: () => write('/auth/logout', 'POST'),
  updateProfile: (displayName) => write('/me/profile', 'PATCH', { displayName }),
}

export function errorMessage(error) {
  return {
    INVALID_CREDENTIALS: '邮箱或密码不正确，请重试。',
    INVALID_REQUEST: '输入有误，请检查后再试。',
    EMAIL_ALREADY_REGISTERED: '这个邮箱已注册，可以直接登录。',
    CSRF_INVALID: '页面的验证信息已失效，请重新提交。',
    SESSION_UNAVAILABLE: '账号服务暂时不可用，请稍后重试。',
    NETWORK_UNAVAILABLE: '无法连接账号服务，请检查连接后重试。',
  }[error?.code] || '暂时无法完成操作，请稍后重试。'
}
