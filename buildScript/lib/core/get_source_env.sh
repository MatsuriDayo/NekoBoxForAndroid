if [ ! -z $ENV_NB4A ]; then
  export COMMIT_SING_BOX_EXTRA="da009b57ce228c47db8c8b6c9c699aa6eaba2945"
fi

if [ ! -z $ENV_SING_BOX_EXTRA ]; then
  source libs/get_source_env.sh
  # export COMMIT_SING_BOX="91495e813068294aae506fdd769437c41dd8d3a3"
fi
