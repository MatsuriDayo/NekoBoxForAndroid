if [ ! -z $ENV_NB4A ]; then
  export COMMIT_SING_BOX_EXTRA="c0127f6e833ecad55f9ba8f6ece60c9a8d7acd86"
fi

if [ ! -z $ENV_SING_BOX_EXTRA ]; then
  source libs/get_source_env.sh
  # export COMMIT_SING_BOX="91495e813068294aae506fdd769437c41dd8d3a3"
fi
