import { useConfig } from 'nextra-theme-docs'
import { useRouter } from 'next/router'

const Logo = () => (
  <span style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontWeight: 700 }}>
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M6 11V8a6 6 0 1 1 12 0v3h1a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2h1Zm2 0h8V8a4 4 0 1 0-8 0v3Zm4 4a1.5 1.5 0 0 0-.75 2.8V19a.75.75 0 0 0 1.5 0v-1.2A1.5 1.5 0 0 0 12 15Z"
        fill="#7F52FF"
      />
    </svg>
    <span>Passkeys KMP</span>
  </span>
)

export default {
  logo: <Logo />,
  project: {
    link: 'https://github.com/AndroidPoet/passkeys-kmp',
  },
  docsRepositoryBase: 'https://github.com/AndroidPoet/passkeys-kmp/tree/main/website',
  color: {
    hue: 255,
    saturation: 100,
  },
  footer: {
    content: (
      <span>
        MIT © {new Date().getFullYear()}{' '}
        <a href="https://github.com/AndroidPoet/passkeys-kmp" target="_blank" rel="noreferrer">
          Passkeys KMP
        </a>
        . One passkeys API for Kotlin Multiplatform.
      </span>
    ),
  },
  head: function useHead() {
    const { frontMatter } = useConfig()
    const { asPath } = useRouter()
    const pageTitle = frontMatter?.title
    const title = pageTitle ? `${pageTitle} – Passkeys KMP` : 'Passkeys KMP'
    const description =
      frontMatter?.description ??
      'Passkeys KMP — one common passkeys API for Kotlin Multiplatform, backed by real native authenticators on Android, iOS, macOS, Windows, Linux, Wasm and JVM/Compose Desktop.'
    const base = 'https://androidpoet.github.io/passkeys-kmp'
    const path = asPath === '/' ? '' : asPath.split('?')[0].split('#')[0]
    const canonical = `${base}${path}`
    const ogImage = `${base}/favicon.svg`
    return (
      <>
        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
        <title>{title}</title>
        <meta name="description" content={description} />
        <link rel="canonical" href={canonical} />
        <link rel="icon" href={`${base}/favicon.svg`} type="image/svg+xml" />
        <meta name="theme-color" content="#7F52FF" />
        <meta property="og:type" content="website" />
        <meta property="og:site_name" content="Passkeys KMP" />
        <meta property="og:url" content={canonical} />
        <meta property="og:title" content={pageTitle ?? 'Passkeys KMP'} />
        <meta property="og:description" content={description} />
        <meta property="og:image" content={ogImage} />
        <meta name="twitter:card" content="summary_large_image" />
        <meta name="twitter:title" content={pageTitle ?? 'Passkeys KMP'} />
        <meta name="twitter:description" content={description} />
        <meta name="twitter:image" content={ogImage} />
      </>
    )
  },
  sidebar: {
    defaultMenuCollapseLevel: 1,
  },
  toc: {
    backToTop: true,
  },
  navigation: {
    prev: true,
    next: true,
  },
  darkMode: true,
}
