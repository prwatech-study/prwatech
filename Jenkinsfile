pipeline{
    agent any
    
    environment {
        GIT_CREDENTIALS = credentials('gitHub')
    }
    stages{
        stage("Clone Code"){
            steps{
                git branch:"development", url:"https://github.com/prwatech-study/prwatech.git", credentialsId: 'gitHub'
            }
        }
        stage("Build ,Test and Deploy"){
            steps{
                sh "docker-compose down && docker-compose up -d --build prwatech-prod"
            }
        }
    }
}
