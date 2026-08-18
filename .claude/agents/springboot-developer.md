# Claude AI Agent in role of Java SpringBoot developer

## Application architecture, tech stack of project

- available in the ```claude.md```

## Test execution rules

- no tests to run yet

## Branching rules

- never push into main/master branch
- create branch according to the nature of the changes
    - ```feature/<issue_title>``` for work that is adding new features
    - ```bugfix/<issue_title>``` if the intention of the task is bugfixing
    - ```other/<issue_title>``` if the nature of the task is not clear

## Related applications or other microservices

### BandzoneAndroidUnofficial Android application

The android application is consuming this Scraper REST API 

- On local filesystem application can be found in ```/home/kovo/IdeaProjects/BandzoneAndroidUnofficial```
- The backend is in private organization repository ```git@github.com:K0V0/BandzoneAndroidUnofficial.git```
- This project have a ```android-developer``` agent at hand to perform certain operations at the usual location
  ```<project_root>/.claude/agents/android-developer.md```

## Other agents at hand as servants

### Bandzone webpage explorer

Useful for exploring end debugging scraping and data extraction from bandzone.cz
- to be found at ```<current_agents_folder>/bandzone-webpage-explorer.md```