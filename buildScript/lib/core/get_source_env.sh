if [ ! -z $ENV_NB4A ]; then
  export COMMIT_SING_BOX_EXTRA="3805838008319a97e4495f43e10a1d4c9c1e512a"
fi

if [ ! -z $ENV_SING_BOX_EXTRA ]; then
  source libs/get_source_env.sh
  # export COMMIT_SING_BOX="91495e813068294aae506fdd769437c41dd8d3a3"
fi
