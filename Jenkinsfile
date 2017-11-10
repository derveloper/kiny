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

  stage 'build docker image'
  def customImage = docker.build("derveloper/kiny:${env.BUILD_ID}")

  stage 'push docker image'
  customImage.push()
  customImage.push('latest')

  stage 'deploy docker image'
  echo "Deploying derveloper/kiny:${env.BUILD_ID}"
}