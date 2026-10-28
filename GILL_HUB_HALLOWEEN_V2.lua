-- GILL HUB | MM2 HALLOWEEN | experimental client-side Luau
-- Official MM2 place: 142823291. No anti-ban guarantee.
-- Game updates can change candy objects, bag UI, and tool behavior.

local Players = game:GetService('Players')
local UIS = game:GetService('UserInputService')
local TeleportService=game:GetService('TeleportService')
local HttpService=game:GetService('HttpService')
local vimOK, VirtualInputManager = pcall(function()return game:GetService('VirtualInputManager')end)
local Me = Players.LocalPlayer
if game.PlaceId ~= 142823291 then warn('GILL HUB: This is intended for official MM2 (142823291).') end

local sharedEnv = (getgenv and getgenv()) or _G
if type(sharedEnv.GillHalloweenUnload) == 'function' then pcall(sharedEnv.GillHalloweenUnload) end

local alive = true
local C = {}
local S = {
    farm = false, farmNoclip = true, farmHover = true, onlyCandy = false,
    murder = false, sheriff = false, innocent = false,
    prestige = false, antiFling = true, esp = false, lowFX = false,
    loopEmote = false, autoConfirm = false, friendHop = false, reExecute = false, antiAFK = false, extraHotbar = false, hotbarLocked = false
}
local speed, bagGoal, animSpeed, hotbarSize = 20, 40, 1, 100
local observed, bagCurrent, bagMaximum = 0, nil, nil
local manualFull, roundStamp = false, 0
local lastAction, lastPrestige, lastScan = 0, 0, 0
local roleCache, lastRoleRequest = {}, 0
local candidates, currentTarget, recentlyTried, lastContainer = {}, nil, {}, nil
local originalCollision, originalFX = {}, {}
local highlights, previousCharacter = {}, nil
local activeTrack
local loaderURL = 'https://raw.githubusercontent.com/gilljattscripts/gill-hubscript/main/GILL_HUB_HALLOWEEN_V2.lua'
-- Optional settings carried by the executor's teleport queue.
local remembered=sharedEnv.GillHalloweenResume
if type(remembered)=='table' then
    if type(remembered.settings)=='table' then
        for key,value in pairs(remembered.settings) do
            if S[key]~=nil and type(value)=='boolean' then S[key]=value end
        end
    end
    if type(remembered.loaderURL)=='string' then loaderURL=remembered.loaderURL end
    if type(remembered.speed)=='number' then speed=math.clamp(remembered.speed,1,30) end
    if type(remembered.hotbarSize)=='number' then hotbarSize=math.clamp(remembered.hotbarSize,70,140) end
    sharedEnv.GillHalloweenResume=nil
end
local hopBusy, lastHop, flingBusy, cancelFling = false, -100, false, false
local selectedPack = nil
local packOverrides = {}
local savedAnimate = {}
local palette = {
    bg = Color3.fromRGB(16, 16, 22), card = Color3.fromRGB(28, 28, 38),
    raised = Color3.fromRGB(38, 37, 50), orange = Color3.fromRGB(255, 119, 39),
    text = Color3.fromRGB(249, 243, 235), dim = Color3.fromRGB(170, 163, 165),
    green = Color3.fromRGB(87, 201, 133)
}
local function bind(x) C[#C+1] = x; return x end
local function new(class, props, parent)
    local obj = Instance.new(class)
    for k,v in pairs(props or {}) do obj[k] = v end
    obj.Parent = parent
    return obj
end
local function corner(obj, size)
    new('UICorner', {CornerRadius = UDim.new(0, size or 9)}, obj)
end
local function characterOf(p)
    local ch = p.Character
    local h = ch and ch:FindFirstChildOfClass('Humanoid')
    return ch, h, ch and ch:FindFirstChild('HumanoidRootPart')
end
local function getTool(p, name)
    local ch = p.Character
    local backpack = p:FindFirstChildOfClass('Backpack')
    return (ch and ch:FindFirstChild(name)) or (backpack and backpack:FindFirstChild(name))
end
local function roleOf(p)
    local known=roleCache[p.Name]
    if known=='Murderer' or known=='Sheriff' or known=='Innocent' or known=='Hero' then
        return known=='Hero' and 'Sheriff' or known
    end
    if getTool(p, 'Knife') then return 'Murderer' end
    if getTool(p, 'Gun') then return 'Sheriff' end
    return 'Unknown'
end
-- Optional official-MM2 data lookup. No guessed kill/coin remotes are called.
local function refreshRoles()
    if os.clock()-lastRoleRequest<5 then return end
    lastRoleRequest=os.clock()
    local rs=game:GetService('ReplicatedStorage')
    local remotes=rs:FindFirstChild('Remotes')
    local extras=remotes and remotes:FindFirstChild('Extras')
    local remote=extras and extras:FindFirstChild('GetPlayerData')
    if not remote or not remote:IsA('RemoteFunction') then return end
    task.spawn(function()
        local ok,result=pcall(function()return remote:InvokeServer()end)
        if not alive or not ok or type(result)~='table' then return end
        local nextRoles={}
        for name,info in pairs(result) do
            if type(name)=='string' and type(info)=='table' and type(info.Role)=='string' then
                nextRoles[name]=info.Role
            end
        end
        roleCache=nextRoles
    end)
end
local function equip(tool)
    local ch,h = characterOf(Me)
    if tool and h and tool.Parent ~= ch then pcall(function()h:EquipTool(tool)end) end
end
local function alivePlayer(p)
    local _,h,r = characterOf(p)
    return h and h.Health > 0 and r
end
local function allPlayers()
    local out = {}
    for _,p in ipairs(Players:GetPlayers()) do
        if p ~= Me and alivePlayer(p) then out[#out+1] = p end
    end
    return out
end
local function resetRound()
    roundStamp = roundStamp + 1
    observed, bagCurrent, bagMaximum = 0, nil, nil
    manualFull, currentTarget = false, nil
    candidates, recentlyTried = {}, {}
    lastAction = 0
end

-- UI (no external UI library, images, or network downloads)
local gui = new('ScreenGui', {Name='GillHalloweenHub', ResetOnSpawn=false, ZIndexBehavior=Enum.ZIndexBehavior.Sibling}, nil)
local ok = pcall(function() gui.Parent = game:GetService('CoreGui') end)
if not ok or not gui.Parent then gui.Parent = Me:WaitForChild('PlayerGui') end
local bubble = new('TextButton', {
    Name='Open', Size=UDim2.fromOffset(62,55), Position=UDim2.new(0,10,.38,0),
    BackgroundColor3=Color3.fromRGB(21,23,31), Text='', TextColor3=Color3.new(1,1,1),
    Font=Enum.Font.GothamBlack, TextSize=14
}, gui)
corner(bubble,14)
new('UIStroke',{Color=palette.orange,Thickness=2,Transparency=.05},bubble)
new('UIGradient',{Color=ColorSequence.new(Color3.fromRGB(52,36,42),Color3.fromRGB(12,14,24)),Rotation=38},bubble)
local insignia=new('Frame',{Size=UDim2.fromOffset(34,34),Position=UDim2.new(.5,-17,.5,-21),Rotation=45,BackgroundColor3=Color3.fromRGB(66,30,28)},bubble)
corner(insignia,7)
new('UIStroke',{Color=palette.orange,Thickness=1.2},insignia)
new('TextLabel',{BackgroundTransparency=1,Text='G',Font=Enum.Font.GothamBlack,TextSize=27,Rotation=-45,TextColor3=palette.text,Size=UDim2.fromScale(1,1)},insignia)
new('TextLabel',{BackgroundTransparency=1,Text='HUB',Font=Enum.Font.GothamBold,TextSize=8,TextColor3=palette.orange,Position=UDim2.new(0,0,1,-13),Size=UDim2.new(1,0,0,11)},bubble)
local panel = new('Frame', {
    Name='Window', Size=UDim2.fromOffset(328,410), Position=UDim2.new(.5,-164,.5,-205),
    BackgroundColor3=palette.bg, Visible=false
}, gui)
corner(panel,13)
new('UIGradient',{Color=ColorSequence.new(Color3.fromRGB(26,25,34),Color3.fromRGB(13,15,21)),Rotation=100},panel)
new('Frame',{Position=UDim2.fromOffset(10,105),Size=UDim2.new(1,-20,0,1),BackgroundColor3=Color3.fromRGB(78,51,45),BorderSizePixel=0},panel)
new('UIStroke', {Color=palette.orange, Thickness=1.4, Transparency=.23}, panel)
local header = new('Frame', {BackgroundColor3=palette.card, Size=UDim2.new(1,0,0,65), BorderSizePixel=0}, panel)
corner(header,12)
new('Frame',{Size=UDim2.fromOffset(4,45),Position=UDim2.fromOffset(0,10),BorderSizePixel=0,BackgroundColor3=palette.orange},header)
new('UIGradient', {Color=ColorSequence.new({
    ColorSequenceKeypoint.new(0,Color3.fromRGB(70,32,26)),
    ColorSequenceKeypoint.new(1,Color3.fromRGB(24,24,33))
})}, header)
new('TextLabel', {
    Position=UDim2.fromOffset(13,4), Size=UDim2.new(1,-56,0,30), BackgroundTransparency=1,
    Text='G I L L  /  H U B', TextXAlignment=Enum.TextXAlignment.Left,
    TextColor3=palette.text, Font=Enum.Font.GothamBlack, TextSize=18
},header)
new('TextLabel', {
    Position=UDim2.fromOffset(15,33), Size=UDim2.new(1,-40,0,21), BackgroundTransparency=1,
    Text='MURDER MYSTERY 2  /  HALLOWEEN', TextXAlignment=Enum.TextXAlignment.Left,
    TextColor3=palette.dim, Font=Enum.Font.GothamBold, TextSize=10
},header)
local exit = new('TextButton', {
    Position=UDim2.new(1,-42,0,11), Size=UDim2.fromOffset(30,30),
    BackgroundColor3=palette.raised, Text='×', TextSize=25,
    TextColor3=palette.text, Font=Enum.Font.GothamBold
},header)
corner(exit,8)
local tabStrip = new('ScrollingFrame', {
    Position=UDim2.fromOffset(8,69), Size=UDim2.new(1,-16,0,34),
    BackgroundTransparency=1, ScrollBarThickness=0, ScrollingDirection=Enum.ScrollingDirection.X,
    AutomaticCanvasSize=Enum.AutomaticSize.X, CanvasSize=UDim2.fromOffset(0,0)
},panel)
new('UIListLayout', {FillDirection=Enum.FillDirection.Horizontal, Padding=UDim.new(0,5)},tabStrip)
local body = new('Frame', {
    Position=UDim2.fromOffset(8,109), Size=UDim2.new(1,-16,1,-146), BackgroundTransparency=1
},panel)
local status = new('TextLabel', {
    Position=UDim2.new(0,11,1,-31), Size=UDim2.new(1,-22,0,23), BackgroundTransparency=1,
    Text='Ready | Official MM2 | Experimental modules', TextXAlignment=Enum.TextXAlignment.Left,
    TextColor3=palette.dim, Font=Enum.Font.Gotham, TextSize=11, TextTruncate=Enum.TextTruncate.AtEnd
},panel)
local pages, tabButtons = {}, {}
local function tab(title)
    local page = new('ScrollingFrame', {
        Name=title, Size=UDim2.fromScale(1,1), BackgroundTransparency=1,
        Visible=false, ScrollBarThickness=2, ScrollBarImageColor3=palette.orange,
        AutomaticCanvasSize=Enum.AutomaticSize.Y, CanvasSize=UDim2.fromOffset(0,0)
    },body)
    new('UIListLayout', {Padding=UDim.new(0,6)},page)
    pages[title] = page
    local btn = new('TextButton', {
        Size=UDim2.fromOffset(math.max(61,9*#title+13),30), BackgroundColor3=palette.card,
        Text=title, Font=Enum.Font.GothamBold, TextSize=12, TextColor3=palette.text
    },tabStrip)
    corner(btn,7)
    tabButtons[title]=btn
    bind(btn.MouseButton1Click:Connect(function()
        for k,p in pairs(pages) do
            p.Visible = (k == title)
            tabButtons[k].BackgroundColor3 = (k == title) and palette.orange or palette.card
        end
    end))
    return page
end
local farmTab = tab('Farm')
local roleTab = tab('Roles')
local viewTab = tab('Visuals')
local emoteTab = tab('Animations')
local guardTab = tab('Protection')
local flingTab = tab('Fling')
local toolsTab = tab('Hotbar')
local friendsTab = tab('Friends')
local settingsTab = tab('Settings')
local function label(page, title, sub)
    return new('TextLabel', {
        Size=UDim2.new(1,-3,0,sub and 45 or 29), BackgroundTransparency=1,
        Text=title..(sub and '\n'..sub or ''), TextWrapped=true,
        TextXAlignment=Enum.TextXAlignment.Left, TextYAlignment=Enum.TextYAlignment.Center,
        Font=Enum.Font.GothamBold, TextSize=12, TextColor3=palette.dim
    },page)
end
local function action(page,title,fn)
    local b=new('TextButton', {
        Size=UDim2.new(1,-3,0,35), BackgroundColor3=palette.raised, Text=title,
        TextColor3=palette.text, Font=Enum.Font.GothamMedium, TextSize=13
    },page)
    corner(b,7)
    bind(b.MouseButton1Click:Connect(function()if alive then fn(b)end end))
    return b
end
local toggleButtons={}
local function refreshSwitches()
    for key,item in pairs(toggleButtons) do
        local on=not not S[key]
        item.track.BackgroundColor3=on and palette.orange or Color3.fromRGB(65,65,77)
        item.dot.Position=on and UDim2.new(1,-19,.5,-8) or UDim2.new(0,3,.5,-8)
        item.row.BackgroundColor3=on and Color3.fromRGB(47,34,37) or palette.raised
    end
end
local function toggle(page,title,key)
    local row=new('Frame',{
        Size=UDim2.new(1,-3,0,43), BackgroundColor3=palette.raised
    },page)
    corner(row,8)
    new('TextLabel',{
        Position=UDim2.fromOffset(10,0),Size=UDim2.new(1,-74,1,0),
        Text=title,TextWrapped=true,TextXAlignment=Enum.TextXAlignment.Left,
        BackgroundTransparency=1,TextSize=12,Font=Enum.Font.GothamMedium,
        TextColor3=palette.text
    },row)
    local track=new('Frame',{
        Position=UDim2.new(1,-58,.5,-11),Size=UDim2.fromOffset(45,22),
        BackgroundColor3=Color3.fromRGB(65,65,77)
    },row)
    corner(track,11)
    local dot=new('Frame',{
        Position=UDim2.new(0,3,.5,-8),Size=UDim2.fromOffset(16,16),
        BackgroundColor3=Color3.fromRGB(247,244,238)
    },track)
    corner(dot,8)
    local hit=new('TextButton',{
        Name='Toggle',BackgroundTransparency=1,Text='',Size=UDim2.fromScale(1,1)
    },row)
    toggleButtons[key]={row=row,track=track,dot=dot}
    bind(hit.MouseButton1Click:Connect(function()
        if not alive then return end
        S[key]=not S[key]
        refreshSwitches()
    end))
    refreshSwitches()
    return hit
end
local function range(page,title,min,max,value,step,callback)
    local n = value
    local row=new('Frame',{Size=UDim2.new(1,-3,0,39),BackgroundColor3=palette.card},page)
    corner(row,7)
    local t=new('TextLabel',{Size=UDim2.new(1,-99,1,0),Position=UDim2.fromOffset(9,0),BackgroundTransparency=1,
        Text=title..': '..n,TextXAlignment=Enum.TextXAlignment.Left,Font=Enum.Font.GothamMedium,
        TextSize=12,TextColor3=palette.text},row)
    for i,sym in ipairs({'−','+'}) do
        local b=new('TextButton',{Position=UDim2.new(1,(i==1) and -90 or -45,0,4),Size=UDim2.fromOffset(40,31),
            BackgroundColor3=palette.raised,Text=sym,TextColor3=palette.text,
            TextSize=20,Font=Enum.Font.GothamBold},row)
        corner(b,6)
        bind(b.MouseButton1Click:Connect(function()
            n=math.clamp(n+(i==1 and -step or step),min,max)
            t.Text=title..': '..n
            callback(n)
        end))
    end
end
local function drag(handle,target,canDrag)
    local dragging, origin, start, touch
    bind(handle.InputBegan:Connect(function(input)
        if canDrag and not canDrag() then return end
        local ty=input.UserInputType
        if ty==Enum.UserInputType.Touch or ty==Enum.UserInputType.MouseButton1 then
            dragging=true; origin=input.Position; start=target.Position
            touch=(ty==Enum.UserInputType.Touch) and input or nil
        end
    end))
    bind(UIS.InputChanged:Connect(function(input)
        if not dragging or (canDrag and not canDrag()) then return end
        if (touch and input==touch) or (not touch and input.UserInputType==Enum.UserInputType.MouseMovement) then
            local delta=input.Position-origin
            target.Position=UDim2.new(start.X.Scale,start.X.Offset+delta.X,start.Y.Scale,start.Y.Offset+delta.Y)
        end
    end))
    bind(UIS.InputEnded:Connect(function(input)
        if dragging and ((touch and input==touch) or (not touch and input.UserInputType==Enum.UserInputType.MouseButton1)) then
            dragging=false;touch=nil
        end
    end))
end
drag(bubble,bubble)
drag(header,panel)
bind(bubble.MouseButton1Click:Connect(function()panel.Visible=not panel.Visible end))
bind(exit.MouseButton1Click:Connect(function()panel.Visible=false end))

-- Optional compact tool picker for mobile; only equips real tools in your Backpack/character.
local hotbarPanel=new('Frame',{
    Name='ExtraHotbar', Size=UDim2.fromOffset(312,82), Position=UDim2.new(.5,-156,1,-154),
    Visible=S.extraHotbar, BackgroundColor3=palette.bg
},gui)
corner(hotbarPanel,11)
new('UIStroke',{Color=palette.orange,Thickness=1.25,Transparency=.23},hotbarPanel)
local hotbarScale=new('UIScale',{Scale=hotbarSize/100},hotbarPanel)
local hotbarGrip=new('TextButton',{
    Size=UDim2.new(1,-16,0,22),Position=UDim2.fromOffset(8,4),
    BackgroundColor3=palette.card,Text=S.hotbarLocked and 'HOTBAR | LOCKED' or 'HOTBAR | HOLD TO DRAG',
    TextColor3=palette.dim,TextSize=10,Font=Enum.Font.GothamBold,AutoButtonColor=false
},hotbarPanel)
corner(hotbarGrip,6)
drag(hotbarGrip,hotbarPanel,function()return not S.hotbarLocked end)
local hotbarList=new('ScrollingFrame',{
    Position=UDim2.fromOffset(8,30),Size=UDim2.new(1,-16,1,-38),BackgroundTransparency=1,
    ScrollingDirection=Enum.ScrollingDirection.X,ScrollBarThickness=2,ScrollBarImageColor3=palette.orange,
    AutomaticCanvasSize=Enum.AutomaticSize.X,CanvasSize=UDim2.fromOffset(0,0)
},hotbarPanel)
new('UIListLayout',{FillDirection=Enum.FillDirection.Horizontal,Padding=UDim.new(0,5)},hotbarList)
local hotbarSignature=''
local function refreshHotbar(force)
    if not alive or not S.extraHotbar then return end
    local backpack=Me:FindFirstChildOfClass('Backpack')
    local char=Me.Character
    local tools={}
    for _,container in ipairs({backpack,char}) do
        if container then
            for _,item in ipairs(container:GetChildren()) do
                if item:IsA('Tool') then tools[#tools+1]=item end
            end
        end
    end
    table.sort(tools,function(a,b)return a.Name<b.Name end)
    local signature={}
    for _,tool in ipairs(tools) do
        signature[#signature+1]=tostring(tool)
    end
    local key=table.concat(signature,'|')
    if not force and key==hotbarSignature then
        for _,item in ipairs(hotbarList:GetChildren()) do
            if item:IsA('TextButton') then
                local tool=item:FindFirstChild('BoundTool')
                if tool and tool:IsA('ObjectValue') and tool.Value then
                    item.BackgroundColor3=tool.Value.Parent==char and palette.orange or palette.raised
                end
            end
        end
        return
    end
    hotbarSignature=key
    for _,item in ipairs(hotbarList:GetChildren()) do
        if not item:IsA('UIListLayout') then item:Destroy() end
    end
    if #tools==0 then
        new('TextLabel',{Size=UDim2.fromOffset(285,42),BackgroundTransparency=1,
            Text='No equipped / backpack tools available',TextSize=12,
            Font=Enum.Font.Gotham,TextColor3=palette.dim},hotbarList)
        return
    end
    for _,tool in ipairs(tools) do
        local equipped=tool.Parent==char
        local btn=new('TextButton',{
            Size=UDim2.fromOffset(92,42),BackgroundColor3=equipped and palette.orange or palette.raised,
            Text=tool.Name,TextTruncate=Enum.TextTruncate.AtEnd,TextColor3=palette.text,
            TextSize=11,Font=Enum.Font.GothamBold
        },hotbarList)
        corner(btn,8)
        new('ObjectValue',{Name='BoundTool',Value=tool},btn)
        btn.MouseButton1Click:Connect(function()
            local current,bHum=characterOf(Me)
            local pack=Me:FindFirstChildOfClass('Backpack')
            if not bHum or (tool.Parent~=current and tool.Parent~=pack) then return end
            if tool.Parent==current then bHum:UnequipTools() else equip(tool) end
            task.delay(.1,function()if alive then refreshHotbar(false) end end)
        end)
    end
end


-- Farm targets: standard MM2 CoinContainer, plus recognizable event-candy names.
local function collectible(part, fromContainer)
    if not part:IsA('BasePart') or not part.Parent then return false end
    if part:IsDescendantOf(Me.Character or workspace.Terrain) then return false end
    local n=part.Name:lower()
    if n:find('candy') or n:find('coin_server') or n=='coin' then return true end
    if fromContainer and (n:find('coin') or n:find('collect')) then return true end
    if fromContainer and part.Parent:IsA('Model') then
        local modelName=part.Parent.Name:lower()
        if modelName:find('coin') or modelName:find('candy') then return true end
    end
    return false
end
local function scan()
    local found={}
    local container=workspace:FindFirstChild('CoinContainer',true)
    if container then
        for _,p in ipairs(container:GetDescendants()) do
            if collectible(p,true) and (not S.onlyCandy or p.Name:lower():find('candy')) then found[#found+1]=p end
            if #found>=500 then break end
        end
    else
        -- Event variants sometimes put a CandyContainer directly in the map.
        local other=workspace:FindFirstChild('CandyContainer',true)
        if other then
            for _,p in ipairs(other:GetDescendants()) do
                if collectible(p,true) then found[#found+1]=p end
                if #found>=500 then break end
            end
        end
    end
    local activeContainer=container or workspace:FindFirstChild('CandyContainer',true)
    if activeContainer and lastContainer and activeContainer~=lastContainer then
        resetRound()
        roleCache={}
    end
    if activeContainer then lastContainer=activeContainer end
    candidates=found
end
local function nearestCoin(root)
    local best,dist=nil,math.huge
    local now=os.clock()
    for _,p in ipairs(candidates) do
        if p.Parent and (not recentlyTried[p] or recentlyTried[p]<now) then
            local d=(root.Position-p.Position).Magnitude
            if d<dist then best,dist=p,d end
        end
    end
    return best,dist
end
local function restoreCollision()
    for part,old in pairs(originalCollision) do
        if part.Parent then pcall(function()part.CanCollide=old end) end
    end
    table.clear(originalCollision)
end
local function noclipCharacter()
    local ch=Me.Character
    if not ch then return end
    for _,part in ipairs(ch:GetDescendants()) do
        if part:IsA('BasePart') then
            if originalCollision[part]==nil then originalCollision[part]=part.CanCollide end
            part.CanCollide=false
        end
    end
end
local function moveTowards(root,point,dt)
    local displacement=point-root.Position
    local distance=displacement.Magnitude
    if distance < .01 then return true end
    local distancePerTick=math.min(distance, math.clamp(speed,1,30)*math.min(dt,.2))
    local newPosition=root.Position+displacement.Unit*distancePerTick
    root.CFrame=CFrame.new(newPosition)*root.CFrame.Rotation
    if S.farmHover then
        root.AssemblyLinearVelocity=Vector3.zero
        root.AssemblyAngularVelocity=Vector3.zero
    end
    return distance<3.2
end
local function bagTextProgress()
    local pg=Me:FindFirstChildOfClass('PlayerGui')
    if not pg then return nil,nil end
    local checked=0
    for _,guiObj in ipairs(pg:GetDescendants()) do
        if (guiObj:IsA('TextLabel') or guiObj:IsA('TextButton')) and guiObj.Visible and not guiObj:IsDescendantOf(gui) then
            checked=checked+1
            if checked>700 then break end
            local txt=guiObj.Text
            local a,b=txt:match('(%d+)%s*/%s*(%d+)')
            if a and b then
                local parentName=(guiObj.Parent and guiObj.Parent.Name or ''):lower()
                local hint=(guiObj.Name..' '..parentName..' '..txt):lower()
                if hint:find('coin') or hint:find('candy') or hint:find('bag') or hint:find('collect') then
                    a,b=tonumber(a),tonumber(b)
                    if b>0 and b<=200 and a<=b then return a,b end
                end
            end
        end
    end
    return nil,nil
end
local function fullBag()
    if manualFull then return true end
    if bagCurrent and bagMaximum then return bagCurrent>=bagMaximum end
    return S.autoConfirm and observed>=bagGoal
end
local function fullActionAllowed()
    return fullBag() and alivePlayer(Me)
end
local function tryCollect(root,target)
    if not target or not target.Parent then return end
    local close=moveTowards(root,target.Position,.1)
    if close then
        local initialParent=target.Parent
        pcall(function()
            if type(firetouchinterest)=='function' then
                firetouchinterest(root,target,0)
                firetouchinterest(root,target,1)
            end
        end)
        recentlyTried[target]=os.clock()+4
        local stamp=roundStamp
        task.delay(.35,function()
            if alive and stamp==roundStamp and (not target.Parent or target.Parent~=initialParent) then
                observed=observed+1
            end
        end)
    end
end

-- Social privacy: hop on a friend entering this exact server, not just the game.
-- Requires an executor with HTTP support. Never claim this is invisible or ban-proof.
local noticeText, noticeUntil = '', 0
local function say(msg, seconds)
    noticeText = tostring(msg)
    noticeUntil = os.clock() + (seconds or 5)
    status.Text = noticeText
end
local function fetchServers()
    local endpoint=string.format('https://games.roblox.com/v1/games/%d/servers/Public?sortOrder=Asc&limit=100',game.PlaceId)
    local body
    local req=(syn and syn.request) or http_request or request
    if type(req)=='function' then
        local ok,response=pcall(function()return req({Url=endpoint,Method='GET'})end)
        if ok and type(response)=='table' and (response.StatusCode==200 or response.Success) then
            body=response.Body
        end
    end
    if not body then
        local ok,result=pcall(function()return game:HttpGet(endpoint)end)
        if ok then body=result end
    end
    if not body then return nil, 'Server lookup blocked by executor' end
    local ok,data=pcall(function()return HttpService:JSONDecode(body)end)
    if not ok or type(data)~='table' or type(data.data)~='table' then
        return nil, 'Server list unavailable'
    end
    return data.data
end
local function queueResume()
    if not S.reExecute then return true end
    if not loaderURL:match('^https://[%w%._%-%/%%?=&]+$') then
        say('Auto re-execute needs a trusted raw HTTPS script URL',8)
        return false
    end
    local q=(syn and syn.queue_on_teleport) or queue_on_teleport or
        (fluxus and fluxus.queue_on_teleport)
    if type(q)~='function' then
        say('Auto re-execute not available in this Delta session',8)
        return false
    end
    local snapshot=HttpService:JSONEncode({settings=S,loaderURL=loaderURL,speed=speed,hotbarSize=hotbarSize})
    local code=string.format([[
        repeat task.wait(.4) until game:IsLoaded()
        getgenv().GillHalloweenResume=game:GetService('HttpService'):JSONDecode(%q)
        task.wait(2)
        loadstring(game:HttpGet(%q))()
    ]],snapshot,loaderURL)
    local ok=pcall(q,code)
    if not ok then say('Could not queue script for next server',7) end
    return ok
end
local function hopServer(reason)
    if not alive or hopBusy or os.clock()-lastHop<18 then return end
    hopBusy=true;lastHop=os.clock()
    say('Looking for another public server...',8)
    task.spawn(function()
        local servers,err=fetchServers()
        if not alive then return end
        if not servers then
            hopBusy=false;say(err or 'Could not find a server',8);return
        end
        local choices={}
        for _,server in ipairs(servers) do
            if type(server)=='table' and type(server.id)=='string' and
                server.id~=game.JobId and type(server.playing)=='number' and
                type(server.maxPlayers)=='number' and server.playing<server.maxPlayers-1 then
                choices[#choices+1]=server.id
            end
        end
        if #choices==0 then
            hopBusy=false;say('No suitable alternative server found',8);return
        end
        local target=choices[math.random(1,#choices)]
        queueResume() -- optional, requires a hosted loader URL
        say('Teleporting to a new server...',8)
        local ok,e=pcall(function()
            TeleportService:TeleportToPlaceInstance(game.PlaceId,target,Me)
        end)
        if not ok then hopBusy=false;say('Teleport failed: '..tostring(e):sub(1,70),8) end
        -- If the teleport does not proceed, allow another attempt later.
        task.delay(15,function() if alive then hopBusy=false end end)
    end)
end
local function checkFriend(player)
    if player==Me or not player or not S.friendHop then return end
    task.spawn(function()
        local ok,isFriend=pcall(function()return Me:IsFriendsWith(player.UserId)end)
        if alive and S.friendHop and player.Parent==Players and ok and isFriend then
            say('A Roblox friend joined this server; hopping...',6)
            hopServer('friend')
        end
    end)
end
local function checkCurrentFriends()
    for _,p in ipairs(Players:GetPlayers()) do if p~=Me then checkFriend(p)end end
end
bind(Players.PlayerAdded:Connect(checkFriend))

-- Manual fling is intentionally isolated from farming and post-bag actions.
-- It may only affect the local client if the game blocks physics replication.
local function flingTargets(targets)
    if flingBusy then say('Fling already running');return end
    local _,hum,root=characterOf(Me)
    if not root or not hum then say('Character not ready');return end
    flingBusy=true;cancelFling=false
    task.spawn(function()
        local originalCF=root.CFrame
        local oldAnti=S.antiFling
        S.antiFling=false
        for _,p in ipairs(targets) do
            if cancelFling or not alive then break end
            local _,ph,pr=characterOf(p)
            if p~=Me and ph and ph.Health>0 and pr and root.Parent then
                for _=1,12 do
                    if cancelFling or not alive or not pr.Parent or not root.Parent then break end
                    pcall(function()
                        root.AssemblyAngularVelocity=Vector3.new(0,260,0)
                        root.CFrame=pr.CFrame*CFrame.new(0,0,1.8)
                    end)
                    task.wait(.06)
                end
            end
        end
        if root and root.Parent then
            pcall(function()
                root.AssemblyLinearVelocity=Vector3.zero
                root.AssemblyAngularVelocity=Vector3.zero
                root.CFrame=originalCF
            end)
        end
        S.antiFling=oldAnti
        flingBusy=false
    end)
end

-- Role actions are *separate*. Only the detected current role is allowed to act.
-- MM2 may require server-side remotes that generic Tool:Activate cannot invoke.
local function currentRole()
    -- Never infer Innocent from an empty backpack or an active map.
    -- Unknown role must not trigger automatic reset.
    return roleOf(Me)
end
local function closestPlayerTo(root,role)
    local best,d=nil,math.huge
    for _,p in ipairs(allPlayers()) do
        if not role or roleOf(p)==role then
            local _,_,r=characterOf(p)
            local dist=(r.Position-root.Position).Magnitude
            if dist<d then best,d=p,dist end
        end
    end
    return best,d
end
local function afterFull(role,root)
    if os.clock()-lastAction < .8 then return end
    if role=='Murderer' and S.murder then
        local victim,d=closestPlayerTo(root,nil)
        if victim then
            local _,_,vr=characterOf(victim)
            local knife=getTool(Me,'Knife')
            if knife and vr then
                equip(knife)
                if d>7 then moveTowards(root,vr.Position,.1)
                else pcall(function()knife:Activate()end);lastAction=os.clock() end
            end
        end
    elseif role=='Sheriff' and S.sheriff then
        local murderer,d=closestPlayerTo(root,'Murderer')
        local gun=getTool(Me,'Gun')
        if murderer and gun and d<160 then
            local _,_,mr=characterOf(murderer)
            if mr then
                equip(gun)
                local camera=workspace.CurrentCamera
                if camera then camera.CFrame=CFrame.lookAt(camera.CFrame.Position,mr.Position+Vector3.new(0,1.5,0)) end
                pcall(function()gun:Activate()end)
                lastAction=os.clock()
            end
        end
    elseif role=='Innocent' and S.innocent then
        lastAction=os.clock()
        local _,h=characterOf(Me)
        if h then pcall(function()h.Health=0 end) end
    end
end

-- Conservative auto prestige: clicks a currently visible 'Prestige' GUI button.
-- Does not call guessed remote events or perform purchases.
local function tryPrestige()
    if not S.prestige or os.clock()-lastPrestige<35 then return end
    local pg=Me:FindFirstChildOfClass('PlayerGui')
    if not pg then return end
    for _,b in ipairs(pg:GetDescendants()) do
        if (b:IsA('TextButton') or b:IsA('ImageButton')) and b.Visible then
            local title=(b.Name..' '..(b:IsA('TextButton') and b.Text or '')):lower()
            if title:find('prestige') and b.AbsoluteSize.X>1 and b.AbsoluteSize.Y>1 then
                local center=b.AbsolutePosition+b.AbsoluteSize/2
                lastPrestige=os.clock()
                pcall(function()
                    if not vimOK then return end
                    VirtualInputManager:SendMouseButtonEvent(center.X,center.Y,0,true,game,0)
                    VirtualInputManager:SendMouseButtonEvent(center.X,center.Y,0,false,game,0)
                end)
                break
            end
        end
    end
end

-- Effects / ESP / emotes
local function fx(on)
    if on then
        local count=0
        for _,obj in ipairs(workspace:GetDescendants()) do
            if count>=1500 then break end
            if obj:IsA('ParticleEmitter') or obj:IsA('Trail') or obj:IsA('Beam') then
                if originalFX[obj]==nil then originalFX[obj]=obj.Enabled end
                obj.Enabled=false;count=count+1
            end
        end
    else
        for obj,value in pairs(originalFX) do
            if obj.Parent then pcall(function()obj.Enabled=value end) end
        end
        table.clear(originalFX)
    end
end
local function clearESP()
    for _,h in pairs(highlights) do pcall(function()h:Destroy()end) end
    table.clear(highlights)
end
local function updateESP()
    for _,p in ipairs(Players:GetPlayers()) do
        if p~=Me then
            local ch=p.Character
            if ch and alivePlayer(p) then
                local h=highlights[p]
                if not h or h.Parent~=ch then
                    if h then h:Destroy() end
                    h=new('Highlight',{Name='GillRoleESP',DepthMode=Enum.HighlightDepthMode.AlwaysOnTop,
                        FillTransparency=.78,OutlineTransparency=0},ch)
                    highlights[p]=h
                end
                local role=roleOf(p)
                h.OutlineColor=(role=='Murderer' and Color3.fromRGB(255,63,74)) or
                    (role=='Sheriff' and Color3.fromRGB(68,169,255)) or palette.orange
            elseif highlights[p] then highlights[p]:Destroy();highlights[p]=nil end
        end
    end
end
local function stopEmote()
    if activeTrack then pcall(function()activeTrack:Stop(.1);activeTrack:Destroy()end) end
    activeTrack=nil
end
local function playEmote(id)
    local _,hum=characterOf(Me)
    if not hum then return end
    stopEmote()
    local animation=Instance.new('Animation')
    animation.AnimationId='rbxassetid://'..tostring(id)
    local animator=hum:FindFirstChildOfClass('Animator') or new('Animator',{},hum)
    local ok,track=pcall(function()return animator:LoadAnimation(animation)end)
    animation:Destroy()
    if ok and track then
        activeTrack=track
        track.Priority=Enum.AnimationPriority.Action
        track.Looped=S.loopEmote
        track:Play(.15)
        track:AdjustSpeed(animSpeed)
    end
end

-- Roblox R15 animation packs. Some assets may be restricted or overridden by MM2.
local packs={
  Ninja={idle1=656117400,idle2=656118341,walk=656121766,run=656118852,jump=656117878,fall=656115606,climb=656114359,swim=656119721,swimidle=656121397},
  Astronaut={idle1=891621366,idle2=891633237,walk=891667138,run=891636393,jump=891627522,fall=891617961,climb=891609353,swim=891639666,swimidle=891663592},
  Mage={idle1=707742142,idle2=707855907,walk=707897309,run=707861613,jump=707853694,fall=707829716,climb=707826056,swim=707876443,swimidle=707894699},
  Levitation={idle1=616006778,idle2=616008087,walk=616013216,run=616010382,jump=616008936,fall=616005863,climb=616003713,swim=616011509,swimidle=616012453},
  Vampire={idle1=1083445855,idle2=1083450166,walk=1083473930,run=1083462077,jump=1083455352,fall=1083443587,climb=1083439238,swim=1083464683,swimidle=1083467779},
  Robot={idle1=616088211,idle2=616089559,walk=616095330,run=616091570,jump=616090535,fall=616087089,climb=616086039,swim=616092998,swimidle=616094091},
  Zombie={idle1=616158929,idle2=616160636,walk=616168032,run=616163682,jump=616161997,fall=616157476,climb=616156119,swim=616165109,swimidle=616166655}
}
local slots={
 idle1={'idle','Animation1'},idle2={'idle','Animation2'},
 walk={'walk','WalkAnim'},run={'run','RunAnim'},
 jump={'jump','JumpAnim'},fall={'fall','FallAnim'},
 climb={'climb','ClimbAnim'},swim={'swim','Swim'},swimidle={'swimidle','SwimIdle'}
}
local function findAnim(root,slot)
    local path=slots[slot]
    if not root or not path then return nil end
    local branch=root:FindFirstChild(path[1])
    local object=branch and branch:FindFirstChild(path[2])
    return object and object:IsA('Animation') and object or nil
end
local function applyPack()
    local c,h=characterOf(Me)
    if not c or not h then return end
    if h.RigType~=Enum.HumanoidRigType.R15 then say('Animation packs need R15 characters');return end
    local animate=c:FindFirstChild('Animate')
    if not animate then say('Animate controller not found in this game');return end
    local defs=selectedPack and packs[selectedPack] or {}
    for slot in pairs(slots) do
        local animation=findAnim(animate,slot)
        local id=packOverrides[slot] or defs[slot]
        if animation and id then
            if savedAnimate[animation]==nil then savedAnimate[animation]=animation.AnimationId end
            animation.AnimationId='rbxassetid://'..tostring(id)
        end
    end
    pcall(function()
        animate.Disabled=true
        task.wait()
        animate.Disabled=false
    end)
    say((selectedPack or 'Custom')..' animation pack applied',4)
end
local function resetPack()
    selectedPack=nil
    table.clear(packOverrides)
    local c=Me.Character
    for anim,id in pairs(savedAnimate) do
        if anim and anim.Parent and c and anim:IsDescendantOf(c) then
            pcall(function()anim.AnimationId=id end)
        end
    end
    table.clear(savedAnimate)
    local animate=c and c:FindFirstChild('Animate')
    if animate then pcall(function()animate.Disabled=true;task.wait();animate.Disabled=false end)end
    say('Original avatar animations restored',4)
end

label(farmTab,'CANDY / COIN AUTOFARM','Targets known MM2 collection containers. Collection is not guaranteed.')
toggle(farmTab,'Auto collect','farm')
toggle(farmTab,'Noclip while farming','farmNoclip')
toggle(farmTab,'Hover while collecting','farmHover')
toggle(farmTab,'Only objects named candy','onlyCandy')
toggle(farmTab,'Auto full-bag from collected count','autoConfirm')
range(farmTab,'Travel speed (max 30)',1,30,speed,1,function(x)speed=x end)
range(farmTab,'Bag goal if HUD unknown',5,60,bagGoal,5,function(x)bagGoal=x end)
action(farmTab,'Mark bag FULL manually',function()manualFull=true end)
action(farmTab,'Reset collected count',resetRound)

label(roleTab,'AFTER BAG IS FULL','Actions are role-gated. None should run before full-bag detection.')
toggle(roleTab,'Murderer: attack other players','murder')
toggle(roleTab,'Sheriff: aim + fire at knife holder','sheriff')
toggle(roleTab,'Innocent: attempt character reset','innocent')
label(roleTab,'Note','Gun/knife activation depends on MM2 server validation; instant kills cannot be guaranteed.')

label(viewTab,'PLAYER HIGHLIGHTS','Red = visible knife, blue = visible gun, orange = unknown.')
toggle(viewTab,'ESP (1-second refresh)','esp')

label(emoteTab,'R15 AVATAR ANIMATION PACKS','Preset movement packs below. Unavailable animations may be blocked by games.')
local input=new('TextBox',{Size=UDim2.new(1,-3,0,36),BackgroundColor3=palette.raised,
    Text='',PlaceholderText='Paste animation ID',ClearTextOnFocus=false,TextColor3=palette.text,
    PlaceholderColor3=palette.dim,Font=Enum.Font.Gotham,TextSize=13},emoteTab)
corner(input,7)
action(emoteTab,'Play custom animation',function()
    local id=input.Text:match('%d+')
    if id then playEmote(id)end
end)
toggle(emoteTab,'Repeat selected emote','loopEmote')
range(emoteTab,'Animation speed',1,3,1,0.25,function(n)
    animSpeed=n
    if activeTrack then activeTrack:AdjustSpeed(n) end
end)
label(emoteTab,'CUSTOM ACTION EMOTE','Enter your own animation ID, including a moonwalk or dance asset you can access.')
for _,packName in ipairs({'Ninja','Astronaut','Mage','Levitation','Vampire','Robot','Zombie'}) do
    action(emoteTab,'Use full '..packName..' animation pack',function()
        selectedPack=packName
        task.spawn(applyPack)
    end)
end
local customSlot='walk'
local currentSlotButton=action(emoteTab,'Customize slot: WALK (tap to change)',function(b)
    local order={'idle1','idle2','walk','run','jump','fall','climb','swim','swimidle'}
    local index=table.find(order,customSlot) or 1
    customSlot=order[index%#order+1]
    b.Text='Customize slot: '..customSlot:upper()..' (tap to change)'
end)
local slotInput=new('TextBox',{Size=UDim2.new(1,-3,0,36),BackgroundColor3=palette.raised,
    Text='',PlaceholderText='Custom R15 slot animation ID',ClearTextOnFocus=false,
    TextColor3=palette.text,PlaceholderColor3=palette.dim,Font=Enum.Font.Gotham,TextSize=13},emoteTab)
corner(slotInput,7)
action(emoteTab,'Apply ID to selected animation slot',function()
    local id=slotInput.Text:match('(%d+)')
    if id then
        packOverrides[customSlot]=tonumber(id)
        task.spawn(applyPack)
    else say('Enter a valid animation ID') end
end)
action(emoteTab,'Moonwalk animation (use custom ID above)',function()
    say('Use an available moonwalk asset ID in the custom emote field',7)
end)
action(emoteTab,'Restore original avatar animation pack',resetPack)
action(emoteTab,'Stop current emote',stopEmote)
action(emoteTab,'STOP ALL animations / restore avatar',function()
    stopEmote()
    resetPack()
end)


label(toolsTab,'EXPANDED MOBILE HOTBAR','Shows actual tools in your Backpack and character. Cannot grant or unlock items.')
local hotbarSwitch=toggle(toolsTab,'Enable extra tool bar','extraHotbar')
bind(hotbarSwitch.MouseButton1Click:Connect(function()
    task.defer(function()
        if not alive then return end
        hotbarPanel.Visible=S.extraHotbar
        if S.extraHotbar then refreshHotbar(true) else hotbarSignature='' end
    end)
end))
local lockSwitch=toggle(toolsTab,'Lock hotbar position','hotbarLocked')
bind(lockSwitch.MouseButton1Click:Connect(function()
    task.defer(function()
        if alive then
            hotbarGrip.Text=S.hotbarLocked and 'HOTBAR | LOCKED' or 'HOTBAR | HOLD TO DRAG'
        end
    end)
end))
range(toolsTab,'Hotbar size (%)',70,140,hotbarSize,10,function(n)
    hotbarSize=n;hotbarScale.Scale=n/100
end)
action(toolsTab,'Refresh available tools',function()refreshHotbar(true)end)
label(toolsTab,'USE','Drag from its header, resize here, then lock it in place.')

label(guardTab,'LOCAL PROTECTION','Anti-fling reduces extreme velocity, but cannot bypass all fling methods.')
toggle(guardTab,'Velocity anti-fling','antiFling')
toggle(guardTab,'Low-RAM effects reduction','lowFX')
toggle(guardTab,'Prevent idle disconnect (experimental)','antiAFK')
action(guardTab,'Clear velocity now',function()
    local _,_,r=characterOf(Me)
    if r then r.AssemblyLinearVelocity=Vector3.zero;r.AssemblyAngularVelocity=Vector3.zero end
end)

label(flingTab,'PHYSICS / MANUAL FLING','Experimental. May fail if other clients do not replicate character physics.')

local selectedFlingPlayer=nil
local selectedFlingText=label(flingTab,'PLAYER PICKER','Tap a player below. The list updates when players join or leave.')
local pickerFrame=new('ScrollingFrame',{
    Size=UDim2.new(1,-3,0,124),BackgroundColor3=palette.card,ScrollBarThickness=3,
    ScrollBarImageColor3=palette.orange,AutomaticCanvasSize=Enum.AutomaticSize.Y,
    CanvasSize=UDim2.fromOffset(0,0)
},flingTab)
corner(pickerFrame,8)
new('UIListLayout',{Padding=UDim.new(0,4)},pickerFrame)
local function refreshPicker()
    if not alive then return end
    for _,child in ipairs(pickerFrame:GetChildren()) do
        if not child:IsA('UIListLayout') then child:Destroy() end
    end
    if selectedFlingPlayer and selectedFlingPlayer.Parent~=Players then selectedFlingPlayer=nil end
    selectedFlingText.Text=selectedFlingPlayer and ('Selected: '..selectedFlingPlayer.Name)
        or 'Tap a player to select (refreshes automatically)'
    local participants={}
    for _,p in ipairs(Players:GetPlayers()) do
        if p~=Me then participants[#participants+1]=p end
    end
    table.sort(participants,function(a,b)return a.Name:lower()<b.Name:lower() end)
    for _,p in ipairs(participants) do
        local b=new('TextButton',{
            Size=UDim2.new(1,-8,0,29),BackgroundColor3=(selectedFlingPlayer==p) and palette.orange or palette.raised,
            Text=p.DisplayName..'  (@'..p.Name..')',TextColor3=palette.text,
            TextSize=11,TextTruncate=Enum.TextTruncate.AtEnd,Font=Enum.Font.GothamMedium
        },pickerFrame)
        corner(b,6)
        b.MouseButton1Click:Connect(function()
            selectedFlingPlayer=p
            refreshPicker()
        end)
    end
end
bind(Players.PlayerAdded:Connect(function()task.defer(refreshPicker)end))
bind(Players.PlayerRemoving:Connect(function(p)
    if selectedFlingPlayer==p then selectedFlingPlayer=nil end
    task.defer(refreshPicker)
end))
action(flingTab,'Refresh player list',refreshPicker)
action(flingTab,'Fling selected player',function()
    if not selectedFlingPlayer or selectedFlingPlayer.Parent~=Players then
        say('Select a player in the list first')
        return
    end
    flingTargets({selectedFlingPlayer})
end)
task.defer(refreshPicker)

local flingName=new('TextBox',{Size=UDim2.new(1,-3,0,37),BackgroundColor3=palette.raised,
    Text='',PlaceholderText='Enter username or display name',ClearTextOnFocus=false,
    TextColor3=palette.text,PlaceholderColor3=palette.dim,Font=Enum.Font.Gotham,TextSize=12},flingTab)
corner(flingName,7)
action(flingTab,'Attempt fling selected player',function()
    local query=flingName.Text:lower():gsub('%s+','')
    if query=='' then say('Enter a player name');return end
    for _,p in ipairs(Players:GetPlayers()) do
        if p~=Me and (p.Name:lower():find(query,1,true) or p.DisplayName:lower():find(query,1,true)) then
            flingTargets({p});return
        end
    end
    say('Player not found in this server')
end)
action(flingTab,'Attempt fling all players',function()
    local targets=allPlayers()
    if #targets==0 then say('No players available')else flingTargets(targets)end
end)
action(flingTab,'Stop current fling',function()cancelFling=true end)

label(friendsTab,'FRIEND JOIN DETECTOR','When a Roblox friend enters the SAME server, attempt to hop to another public server.')
local friendToggle=toggle(friendsTab,'Auto-hop when a friend joins','friendHop')
bind(friendToggle.MouseButton1Click:Connect(function()
    if S.friendHop then task.defer(checkCurrentFriends) end
end))
action(friendsTab,'Check current server for friends',checkCurrentFriends)
action(friendsTab,'Hop to another public server now',function()hopServer('manual')end)
label(friendsTab,'AUTO RE-EXECUTION','Works only with a trusted HTTPS RAW URL hosting this exact script and executor teleport-queue support.')
local loaderBox=new('TextBox',{Size=UDim2.new(1,-3,0,40),BackgroundColor3=palette.raised,
    Text='',PlaceholderText='Paste your raw script HTTPS URL',ClearTextOnFocus=false,
    TextColor3=palette.text,PlaceholderColor3=palette.dim,Font=Enum.Font.Gotham,TextSize=11},friendsTab)
corner(loaderBox,7)
action(friendsTab,'Save loader URL',function()
    loaderURL=loaderBox.Text:match('^%s*(.-)%s*$') or ''
    say(loaderURL:match('^https://') and 'Loader URL saved for this session' or 'Need a valid HTTPS loader URL',6)
end)
toggle(friendsTab,'Auto re-execute after server hop','reExecute')
label(friendsTab,'PRIVACY LIMIT','This does not hide your Roblox presence. Friends might follow to the new server.')

label(settingsTab,'SETTINGS','No anti-ban promises; use at your own risk.')
toggle(settingsTab,'Auto prestige (visible UI only)','prestige')
action(settingsTab,'Stop farm + role actions',function()
    for _,k in ipairs({'farm','murder','sheriff','innocent'}) do S[k]=false end
    refreshSwitches()
    restoreCollision()
end)
action(settingsTab,'UNLOAD GILL HUB',function()
    if sharedEnv.GillHalloweenUnload then sharedEnv.GillHalloweenUnload() end
end)
-- Select initial tab
if loaderURL~='' then loaderBox.Text=loaderURL end
if S.friendHop then task.defer(checkCurrentFriends) end
for k,p in pairs(pages) do p.Visible=(k=='Farm') end
tabButtons['Farm'].BackgroundColor3=palette.orange

local nextStatus, nextESP, nextFX, nextBag, nextHotbar = 0,0,0,0,0
local function unload()
    if not alive then return end
    alive=false
    stopEmote();restoreCollision();fx(false);clearESP();cancelFling=true
    hotbarPanel.Visible=false
    resetPack()
    for _,c in ipairs(C) do pcall(function()c:Disconnect()end) end
    pcall(function()gui:Destroy()end)
    if sharedEnv.GillHalloweenUnload==unload then sharedEnv.GillHalloweenUnload=nil end
end
sharedEnv.GillHalloweenUnload=unload
bind(Me.CharacterAdded:Connect(function()
    roleCache={}
    stopEmote();restoreCollision();resetRound()
    previousCharacter=Me.Character
    table.clear(savedAnimate)
    if selectedPack or next(packOverrides) then task.delay(1,function()if alive then applyPack()end end) end
end))
bind(Me.Idled:Connect(function()
    if not alive or not S.antiAFK then return end
    local ok,virtual=pcall(function()return game:GetService('VirtualUser')end)
    if ok and virtual then pcall(function()virtual:CaptureController();virtual:ClickButton2(Vector2.zero)end)end
end))
bind(TeleportService.TeleportInitFailed:Connect(function()
    hopBusy=false
    say('Server-hop teleport failed; try again',7)
end))

-- One loop: no per-coin heartbeat connections, minimal allocations.
task.spawn(function()
    local previousClock=os.clock()
    local lastWarning=0
    while alive and gui.Parent do
      local worked,reason=pcall(function()
        local now=os.clock()
        local dt=math.min(now-previousClock,.2)
        previousClock=now
        local ch,hum,root=characterOf(Me)
        if ch~=previousCharacter then previousCharacter=ch;resetRound() end
        if now-lastScan>=2.0 then
            lastScan=now
            refreshRoles()
            if S.farm or S.murder or S.sheriff or S.innocent then scan() end
        end
        if now>=nextBag then
            nextBag=now+1
            bagCurrent,bagMaximum=bagTextProgress()
        end
        if S.extraHotbar and now>=nextHotbar then
            nextHotbar=now+1.2
            refreshHotbar(false)
        end
        local role=currentRole()
        local bagFull=fullBag()
        if root and hum and hum.Health>0 then
            if S.antiFling and root.AssemblyLinearVelocity.Magnitude>130 then
                root.AssemblyLinearVelocity=Vector3.zero
                root.AssemblyAngularVelocity=Vector3.zero
            end
            if S.farm and not bagFull and not flingBusy and not hopBusy then
                if S.farmNoclip then noclipCharacter() else restoreCollision() end
                if not currentTarget or not currentTarget.Parent or (recentlyTried[currentTarget] and recentlyTried[currentTarget]>now) then
                    currentTarget=nearestCoin(root)
                end
                if currentTarget then tryCollect(root,currentTarget) end
            elseif bagFull and fullActionAllowed() and not flingBusy and not hopBusy then
                if S.farmNoclip and role=='Murderer' and S.murder then noclipCharacter()
                else restoreCollision() end
                afterFull(role,root)
            else
                restoreCollision()
            end
        else
            restoreCollision()
        end
        if S.esp and now>=nextESP then nextESP=now+1;updateESP()
        elseif not S.esp and nextESP~=0 then nextESP=0;clearESP() end
        if S.lowFX and now>=nextFX then nextFX=now+20;fx(true)
        elseif not S.lowFX and nextFX~=0 then nextFX=0;fx(false) end
        if now>=nextStatus then
            nextStatus=now+.8
            local progress=bagCurrent and bagMaximum and (bagCurrent..'/'..bagMaximum)
                or (observed..'/'..bagGoal..' estimated')
            if now>=noticeUntil then
                status.Text=string.format('%s | Bag %s | %s',role,progress,
                    bagFull and 'FULL' or (S.farm and 'FARMING' or 'IDLE'))
            end
            tryPrestige()
        end
      end)
      if not worked and os.clock()-lastWarning>8 then
          warn('GILL HUB module error: '..tostring(reason))
          lastWarning=os.clock()
      end
      task.wait((S.farm or flingBusy or S.antiFling) and .1 or .3)
    end
end)
