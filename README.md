# PyDev? +beautify!
Below modification made to add more beautifull look on PyDev.
No special code added, but I've modify the resources (.PNG, .GIF)


<table style="border: none;">
  <tbody>
    <tr style="border:none;">
      <td style="vertical-align: top; padding-top: 10px; border: none;">
* Python File icon, to be used in dark theme or dark background
      </td>
      <td style="vertical-align: top; padding-top: 10px; border: none;">
		<img src="https://raw.github.com/x2nie/Pydev/development/extra/python_file_icon_bug.png" />
      </td>
      <td style="vertical-align: top; padding-top: 10px; border: none;">
		<img src="https://raw.github.com/x2nie/Pydev/development/extra/python_file_icon_bugfixed.png" />
      </td>
    </tr>

    <tr style="border:none;">
      <td style="vertical-align: top; padding-top: 10px; border: none;">
* Python Logotext, in New Project wizard
      </td>
      <td style="vertical-align: top; padding-top: 10px; border: none;">

      </td>
      <td style="vertical-align: top; padding-top: 10px; border: none;">
		<img src="https://raw.github.com/x2nie/Pydev/development/extra/pydev_newprojectwizard1.png" />      </td>
    </tr>

    <tr style="border:none;">
      <td style="vertical-align: top; padding-top: 10px; border: none;">
* Python Logo separated (different picture), in header of New Project wizard
      </td>
      <td style="vertical-align: top; padding-top: 10px; border: none;">

      </td>
      <td style="vertical-align: top; padding-top: 10px; border: none;">
		<img src="https://raw.github.com/x2nie/Pydev/development/extra/pydev_newprojectwizard2.png" />      </td>
    </tr>

    <tr style="border:none;">
      <td style="vertical-align: top; padding-top: 10px; border: none;">
* Python interpreter logo in PyDev Explorer and in perspective
      </td>
      <td style="vertical-align: top; padding-top: 10px; border: none;">

      </td>
      <td style="vertical-align: top; padding-top: 10px; border: none;">
		<img src="https://raw.github.com/x2nie/Pydev/development/extra/python_icon_in_explorer.png" />      </td>
    </tr>

    <tr style="border:none;">
      <td style="vertical-align: top; padding-top: 10px; border: none;">

      </td>
      <td style="vertical-align: top; padding-top: 10px; border: none;">

      </td>
      <td style="vertical-align: top; padding-top: 10px; border: none;">
	</td>
    </tr>
  </tbody>
</table>

## Minor bug Eclipse 4 CSS / Theme
<b>still bug?</b>
When deal with CSS of Eclipse 4 (Kepler), PyDev 3.2.x lost of "StyledText" styles such as dashed line (see screenshoot).
The bug is because the class StyledText has been changed into StyledTextWithoutVerticalBar.

So, if you are using enhanced Eclipse 4 CSS such as Eclipse-Theme / Chrome  https://github.com/jeeeyul/eclipse-themes,
I recomended to add "StyledTextWithoutVerticalBar" to allow styles applied in PyDev editor /PyEdit...
Simply, I have forked the Jeeeyul's project to also allow PyDev here: https://github.com/x2nie/eclipse-themes

![screenshot](https://raw.github.com/x2nie/Pydev/development/extra/pyeditor_css_bug.png)

cheers !!!
:-)
x2nie

