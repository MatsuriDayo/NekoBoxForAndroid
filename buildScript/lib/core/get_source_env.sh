if [ ! -z $ENV_NB4A ]; then
  export COMMIT_SING_BOX_EXTRA="5e69e4d38b195721b9e991c88d64ecdf9dcc51c5"
fi

if [ ! -z $ENV_SING_BOX_EXTRA ]; then
  source libs/get_source_env.sh
  # export COMMIT_SING_BOX="91495e813068294aae506fdd769437c41dd8d3a3"
fi
