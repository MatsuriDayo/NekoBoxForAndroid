if [ ! -z $ENV_NB4A ]; then
  export COMMIT_SING_BOX_EXTRA="6f39a10817860794badf1be7f5c0df3ff5f9a532"
fi

if [ ! -z $ENV_SING_BOX_EXTRA ]; then
  source libs/get_source_env.sh
  # export COMMIT_SING_BOX="91495e813068294aae506fdd769437c41dd8d3a3"
fi
