java  -Dwebdriver.ie.driver=\tmp\webdriver\iedriver\iedriver_2.53.1_x64bit.exe -Dwebdriver.edge.driver="C:\Program Files (x86)\Microsoft Web Driver\MicrosoftWebDriver.exe" -Dwebdriver.chrome.driver=\tmp\webdriver\chromedriver\chromedriver_2.23_32bit.exe -Dwebdriver.gecko.driver=\tmp\webdriver\geckodriver\wires.exe -cp "C:\Projects\kfpenguin\Selenium-Grid-Extras\SeleniumGridExtras\target\classes;\tmp\webdriver\2.53.1.jar" org.openqa.grid.selenium.GridLauncher -role wd  -friendlyHostName VDI-SE-08 -nodeConfig node_5555.json -log log\node_5555.log