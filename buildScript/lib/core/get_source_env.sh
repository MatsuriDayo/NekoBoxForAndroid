if [ ! -z $ENV_NB4A ]; then
  export COMMIT_SING_BOX_EXTRA="df902756f9c50257c48c580e2b5f1b5b190f89bc"
fi

if [ ! -z $ENV_SING_BOX_EXTRA ]; then
  source libs/get_source_env.sh
  # export COMMIT_SING_BOX="91495e813068294aae506fdd769437c41dd8d3a3"
fi
