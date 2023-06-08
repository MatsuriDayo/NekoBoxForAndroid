if [ ! -z $ENV_NB4A ]; then
  export COMMIT_SING_BOX_EXTRA="5dd9ad10dde99a32ee8a0566498e5281b52b0cb7"
fi

if [ ! -z $ENV_SING_BOX_EXTRA ]; then
  source libs/get_source_env.sh
  # export COMMIT_SING_BOX="91495e813068294aae506fdd769437c41dd8d3a3"
fi
