if [ ! -z $ENV_NB4A ]; then
  export COMMIT_SING_BOX_EXTRA="551b4c76cf9d69d24361cf9148b3b2a3f75401ca"
fi

if [ ! -z $ENV_SING_BOX_EXTRA ]; then
  source libs/get_source_env.sh
  # export COMMIT_SING_BOX="91495e813068294aae506fdd769437c41dd8d3a3"
fi
