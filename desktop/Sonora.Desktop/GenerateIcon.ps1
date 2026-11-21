Add-Type -AssemblyName System.Drawing
$size = 256
$bmp = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.Clear([System.Drawing.Color]::Transparent)
$rect = New-Object System.Drawing.Rectangle(8, 8, 240, 240)
$path = New-Object System.Drawing.Drawing2D.GraphicsPath
$radius = 54
$path.AddArc(8, 8, $radius, $radius, 180, 90)
$path.AddArc(248-$radius, 8, $radius, $radius, 270, 90)
$path.AddArc(248-$radius, 248-$radius, $radius, $radius, 0, 90)
$path.AddArc(8, 248-$radius, $radius, $radius, 90, 90)
$path.CloseFigure()
$bg = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, [System.Drawing.Color]::FromArgb(12,42,48), [System.Drawing.Color]::FromArgb(18,112,111), 45)
$g.FillPath($bg, $path)
$pen1 = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(34,235,207), 23)
$pen1.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$pen1.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
$g.DrawBezier($pen1, 169, 64, 93, 35, 49, 99, 108, 119)
$g.DrawBezier($pen1, 108, 119, 196, 145, 207, 185, 112, 195)
$pen2 = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(64,166,255), 13)
$pen2.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$pen2.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
$g.DrawArc($pen2, 157, 38, 55, 55, 215, 235)
$play = New-Object System.Drawing.Point[] 3
$play[0] = New-Object System.Drawing.Point(113, 91)
$play[1] = New-Object System.Drawing.Point(113, 165)
$play[2] = New-Object System.Drawing.Point(174, 128)
$g.FillPolygon([System.Drawing.Brushes]::White, $play)
$png = New-Object System.IO.MemoryStream
$bmp.Save($png, [System.Drawing.Imaging.ImageFormat]::Png)
$bytes = $png.ToArray()
$out = New-Object System.IO.MemoryStream
$writer = New-Object System.IO.BinaryWriter($out)
$writer.Write([UInt16]0); $writer.Write([UInt16]1); $writer.Write([UInt16]1)
$writer.Write([Byte]0); $writer.Write([Byte]0); $writer.Write([Byte]0); $writer.Write([Byte]0)
$writer.Write([UInt16]1); $writer.Write([UInt16]32)
$writer.Write([UInt32]$bytes.Length); $writer.Write([UInt32]22)
$writer.Write($bytes); $writer.Flush()
[System.IO.File]::WriteAllBytes((Join-Path $PSScriptRoot "Sonora.ico"), $out.ToArray())
$g.Dispose(); $bmp.Dispose(); $bg.Dispose(); $path.Dispose(); $pen1.Dispose(); $pen2.Dispose(); $png.Dispose(); $out.Dispose()
