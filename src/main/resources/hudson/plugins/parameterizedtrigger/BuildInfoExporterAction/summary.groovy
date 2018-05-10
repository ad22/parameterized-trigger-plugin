package hudson.plugins.parameterizedtrigger.BuildInfoExporterAction;

def f=namespace(lib.FormTagLib)
def j=namespace(lib.JenkinsTagLib)
def l=namespace(lib.LayoutTagLib)

def builds = my.triggeredBuilds
if(builds.size() > 0) {
	h2("Subproject Builds");

	ul(style:"list-style-type: none;") {
		for (item in builds.sort{it != null && it.hasProperty('description') ? it.description : ''}) {
//		for (item in builds.sort{it.timeInMillis}) {
			li {
				if (item != null) {

					a(href:"${rootURL}/${item.project.url}", class:"model-link") {
						text(item.project.displayName)
					}
					a(href:"${rootURL}/${item.url}", class:"model-link") {
						img(src:"${imagesURL}/16x16/${item.buildStatusUrl}",
								alt:"${item.iconColor.description}", height:"16", width:"16")
						text(item.displayName)
					}

					if (item.hasProperty('description') && item.description != null && item.description != '') {
						raw(' ' + item.description)
	//						item.properties.each { prop, val ->
	//							text(prop + '=' + val + " <br/> ")
	//						}
					}
				}
			}
		}
	}
}

def elasticSearchUri = my.elasticSearchUri
if(uri.startsWith("http://elk.int.fusionio.com:9200")) {
	h2("Subproject Builds (ElasticSearch)");

	ul(style:"list-style-type: none;") {
		for (item in builds.sort{it != null && it.hasProperty('description') ? it.description : ''}) {
//		for (item in builds.sort{it.timeInMillis}) {
			li {
				if (item != null) {

					raw(item.project.displayName)
					a(href:"${elasticSearchUri}/${item.project.displayName}__" + String.valueOf(${item.number}), class:"model-link") {
						img(src:"${imagesURL}/16x16/${item.buildStatusUrl}}",
								alt:"${item.iconColor.description}", height:"16", width:"16")
						text(item.displayName)
					}

					if (item.hasProperty('description') && item.description != null && item.description != '') {
						raw(' ' + item.description)
						//						item.properties.each { prop, val ->
						//							text(prop + '=' + val + " <br/> ")
						//						}
					}
				}
			}
		}
	}
}

def projects = my.triggeredProjects
if (projects.size() > 0) {
	h2("Subprojects triggered but not blocked for");

	ul(style:"list-style-type: none;") {
		for (item in projects) {
			li {
				print "${item}"
				if (item != null) {
					a(href:"${rootURL}/${item.url}", class:"model-link") {
						text(item.displayName)
					}
				}
			}
		}
	}
}

