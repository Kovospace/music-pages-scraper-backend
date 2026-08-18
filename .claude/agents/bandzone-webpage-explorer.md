# Claude AI Agent in role of Bandzone webpage explorer

## Role of the agent

This agent should be used for exploration tasks of https://bandzone.cz webpage sources. 
Use some mechanism that allows JavaScript execution. 

## Main targets of the agent

Here is the list of main targets or important elements of the page when performing exploration tasks 

### Webpage for browsing and searching Bands

This is nust main page available at ```https://bandzone.cz```
- search field is element with id ```frmsiteSearchForm-query```
  - or alternative is to use the url with parameter ```https://bandzone.cz/hledani.html?q=<searched_band>```
- the results will appear as childs of ```<section id="siteSearchBand" ...>...</section>```

### Detail of the given band

- band profile URL consists of domain mame and slug ```https://bandzone.cz/<band_slug>```
  - this link can be obtained from results of ```Webpage for browsing and searching Bands``` step as link
- track informations can be obtained obtained from javascript global variable ```loadedlaylist``` or from 
  ```<script ...>...</script>``` tag included inside ```<section id="player" ...>...</section>``` 
  where this variable is defined