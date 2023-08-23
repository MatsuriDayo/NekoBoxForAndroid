if [ ! -z $ENV_NB4A ]; then
  export COMMIT_SING_BOX_EXTRA="1e43a84c4555250318acfca0a2fe5bc4f3370324"
fi

if [ ! -z $ENV_SING_BOX_EXTRA ]; then
  source libs/get_source_env.sh
  # export COMMIT_SING_BOX="91495e813068294aae506fdd769437c41dd8d3a3"
fi
