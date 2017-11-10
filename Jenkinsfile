node {
  // Mark the code checkout 'stage'....
  stage 'Stage Checkout'

  // Checkout code from repository and update any submodules
  checkout scm

  stage 'Stage Build'

  //branch name from Jenkins environment variables
  echo "branch is: ${env.BRANCH_NAME}"

  sh "./gradlew clean assemble -PBUILD_NUMBER=${env.BUILD_NUMBER}"

  stage 'Stage Archive'
  archiveArtifacts artifacts: 'build/libs/*.jar', fingerprint: true

  //stage 'deploy docker'
  //sh "...."
}