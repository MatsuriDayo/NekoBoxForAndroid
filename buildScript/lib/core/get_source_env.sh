if [ ! -z $ENV_NB4A ]; then
  export COMMIT_SING_BOX_EXTRA="2cc111cc8c059d0654b0a600785f98d93048c67a"
fi

if [ ! -z $ENV_SING_BOX_EXTRA ]; then
  source libs/get_source_env.sh
  # export COMMIT_SING_BOX="91495e813068294aae506fdd769437c41dd8d3a3"
fi
