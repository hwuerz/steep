import ESLintPlugin from "eslint-webpack-plugin"
import styledJsx from "styled-jsx/webpack.js"
import svgToMiniDataURI from "mini-svg-data-uri"

const isProd = process.env.NODE_ENV === "production"

const config = {
  env: {
    // URL to Steep. Used to connect to the event bus.
    // Magic string will be replaced by Steep's HttpEndpoint verticle
    baseUrl: isProd ? "/$$MYBASEURL$$" : "http://localhost:8080"
  },

  // Base path of the web application
  // Magic string will be replaced by Steep's HttpEndpoint verticle
  basePath: isProd ? "/$$MYBASEPATH$$" : "",

  // create a folder for each page
  trailingSlash: true,

  // do not display static optimization indicator
  // it gets in the way of notifications
  devIndicators: {
    autoPrerender: false
  },

  eslint: {
    dirs: ["components", "cypress", "pages"]
  },

  images: {
    // make build compatible with next-optimized-images
    disableStaticImages: true
  },

  experimental: {
    // Set esmExternals to 'loose' to allow highlight-worker.js web worker to be
    // imported. Without this, we'll get an exception.
    esmExternals: "loose",

    // restore scroll position when user navigates back
    scrollRestoration: true
  },

  // list pages to export
  exportPathMap() {
    return {
      "/": { page: "/" },
      "/agents": { page: "/agents" },
      "/agents/[id].html": { page: "/agents/[id]" },
      "/logs/processchains/[id].html": { page: "/logs/processchains/[id]" },
      "/new/workflow": { page: "/new/workflow" },
      "/plugins": { page: "/plugins" },
      "/plugins/[type]/[name].html": { page: "/plugins/[type]/[name]" },
      "/processchains": { page: "/processchains" },
      "/processchains/[id].html": { page: "/processchains/[id]" },
      "/search": { page: "/search" },
      "/services": { page: "/services" },
      "/services/[id].html": { page: "/services/[id]" },
      "/vms": { page: "/vms" },
      "/vms/[id].html": { page: "/vms/[id]" },
      "/workflows": { page: "/workflows" },
      "/workflows/[id].html": { page: "/workflows/[id]" }
    }
  },

  webpack: (config, { dev, defaultLoaders }) => {
    config.module.rules.push({
      test: /\.scss$/,
      use: [
        defaultLoaders.babel,
        {
          loader: styledJsx.loader,
          options: {
            type: (fileName, options) => options.query.type || "scoped"
          }
        },
        "sass-loader"
      ]
    })

    config.module.rules.push({
      test: /\.svg$/,
      type: "asset",
      use: "svgo-loader",
      generator: {
        dataUrl: content => {
          content = content.toString()
          return svgToMiniDataURI(content)
        }
      }
    })

    if (dev) {
      config.plugins.push(new ESLintPlugin({
        extensions: ["js", "jsx"]
      }))
    }

    return config
  }
}

export default config
