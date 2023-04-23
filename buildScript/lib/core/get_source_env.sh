if [ ! -z $ENV_NB4A ]; then
  export COMMIT_SING_BOX_EXTRA="ae31f9f1f745c88fc9430ad5903116536e88bab4"
fi

if [ ! -z $ENV_SING_BOX_EXTRA ]; then
  source libs/get_source_env.sh
  # export COMMIT_SING_BOX="91495e813068294aae506fdd769437c41dd8d3a3"
fi
