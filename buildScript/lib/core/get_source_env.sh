if [ ! -z $ENV_NB4A ]; then
  export COMMIT_SING_BOX_EXTRA="737defe2c9329ceec0105b9334452f8803c1505d"
fi

if [ ! -z $ENV_SING_BOX_EXTRA ]; then
  source libs/get_source_env.sh
  # export COMMIT_SING_BOX="91495e813068294aae506fdd769437c41dd8d3a3"
fi
