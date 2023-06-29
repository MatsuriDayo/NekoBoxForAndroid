if [ ! -z $ENV_NB4A ]; then
  export COMMIT_SING_BOX_EXTRA="9400fd007ad5a19b5892a7c42aae0951e1163747"
fi

if [ ! -z $ENV_SING_BOX_EXTRA ]; then
  source libs/get_source_env.sh
  # export COMMIT_SING_BOX="91495e813068294aae506fdd769437c41dd8d3a3"
fi
