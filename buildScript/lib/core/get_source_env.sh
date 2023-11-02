if [ ! -z $ENV_NB4A ]; then
  export COMMIT_SING_BOX_EXTRA="b13bae46e9fcd330c26e66caedf5640ba31f4505"
fi

if [ ! -z $ENV_SING_BOX_EXTRA ]; then
  source libs/get_source_env.sh
  # export COMMIT_SING_BOX="91495e813068294aae506fdd769437c41dd8d3a3"
fi
